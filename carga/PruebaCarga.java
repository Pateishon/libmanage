import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prueba de carga de LibManage: usuarios virtuales concurrentes (hilos virtuales) que repiten una
 * mezcla realista de operaciones durante un tiempo fijo. Registra latencias por operación y errores.
 *
 * Uso: java carga/PruebaCarga.java URL_BASE ETIQUETA ARCHIVO_CSV SEGUNDOS USUARIOS...
 * Mezcla: 50 % búsqueda avanzada, 15 % detalle, 25 % recomendaciones, 10 % préstamo + devolución.
 */
public final class PruebaCarga {
    private static final String[] CONSULTAS = {"historia", "sistemas", "datos", "redes", "teoria", "practica",
        "analisis", "diseno", "gestion", "algoritmos", "etica", "moderna", "aplicada", "fundamentos", "metodos",
        "gonzalez", "munoz", "rojas", "silva", "torres", "codigo limpio", "ingenieria software", "calculo",
        "introduccion datos", "historia moderna", "sociedad", "politica", "estructuras", "avanzada", "diaz"};
    private static final String[] CATEGORIAS = {"Inform%C3%A1tica", "Historia", "Literatura", "Econom%C3%ADa"};
    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    record Medicion(String operacion, long micros, int estado) {
    }

    public static void main(String[] args) throws Exception {
        String base = args[0];
        String etiqueta = args[1];
        Path csv = Path.of(args[2]);
        int segundos = Integer.parseInt(args[3]);
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .proxy(HttpClient.Builder.NO_PROXY).connectTimeout(Duration.ofSeconds(5))
                .executor(Executors.newVirtualThreadPerTaskExecutor()).build();
        int maxUsuarios = 0;
        for (int i = 4; i < args.length; i++) {
            maxUsuarios = Math.max(maxUsuarios, Integer.parseInt(args[i]));
        }
        List<String> tokens = obtenerTokens(http, base, maxUsuarios);
        if (!Files.exists(csv)) {
            Files.writeString(csv, "version,usuarios,operacion,solicitudes,por_segundo,p50_ms,p95_ms,p99_ms,errores\n");
        }
        for (int i = 4; i < args.length; i++) {
            int usuarios = Integer.parseInt(args[i]);
            List<Medicion> mediciones = ejecutar(http, base, tokens.subList(0, usuarios), segundos);
            StringBuilder filas = new StringBuilder();
            for (String operacion : List.of("TOTAL", "busqueda", "detalle", "recomendaciones", "prestamo")) {
                List<Medicion> grupo = operacion.equals("TOTAL") ? mediciones
                        : mediciones.stream().filter(m -> m.operacion().equals(operacion)).toList();
                List<Long> tiempos = new ArrayList<>(grupo.stream().map(Medicion::micros).toList());
                Collections.sort(tiempos);
                long errores = grupo.stream().filter(m -> m.estado() == 0 || m.estado() >= 500).count();
                filas.append(String.format(java.util.Locale.ROOT, "%s,%d,%s,%d,%.1f,%.1f,%.1f,%.1f,%d%n", etiqueta,
                        usuarios, operacion, grupo.size(), grupo.size() / (double) segundos, pct(tiempos, 50),
                        pct(tiempos, 95), pct(tiempos, 99), errores));
            }
            Files.writeString(csv, filas.toString(), java.nio.file.StandardOpenOption.APPEND);
            System.out.print(filas.toString().lines().filter(l -> l.contains(",TOTAL,") || l.contains("busqueda")
                    || l.contains("recomendaciones")).reduce("", (a, b) -> a + b + "\n"));
        }
    }

    private static List<String> obtenerTokens(HttpClient http, String base, int cantidad) throws Exception {
        List<String> tokens = Collections.synchronizedList(new ArrayList<>());
        Pattern token = Pattern.compile("\"token\":\"([^\"]+)\"");
        try (ExecutorService hilos = Executors.newFixedThreadPool(8)) {
            for (int i = 1; i <= cantidad; i++) {
                int n = i;
                hilos.submit(() -> {
                    String cuerpo = "{\"email\":\"lector" + n + "@uautonoma.cl\",\"contrasena\":\"Lector2026!\"}";
                    HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/api/login"))
                            .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
                    Matcher m = token.matcher(r.body());
                    if (m.find()) {
                        tokens.add(m.group(1));
                    }
                    return null;
                });
            }
        }
        if (tokens.size() < cantidad) {
            throw new IllegalStateException("Solo se obtuvieron " + tokens.size() + " sesiones");
        }
        return tokens;
    }

    private static List<Medicion> ejecutar(HttpClient http, String base, List<String> tokens, int segundos)
            throws InterruptedException {
        List<Medicion> mediciones = Collections.synchronizedList(new ArrayList<>());
        long calentamiento = System.nanoTime() + 2_000_000_000L;
        long fin = calentamiento + segundos * 1_000_000_000L;
        CountDownLatch terminados = new CountDownLatch(tokens.size());
        AtomicInteger semilla = new AtomicInteger();
        try (ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String token : tokens) {
                hilos.submit(() -> {
                    Random azar = new Random(semilla.incrementAndGet());
                    Map<String, Long> estado = new ConcurrentHashMap<>();
                    while (System.nanoTime() < fin) {
                        int dado = azar.nextInt(100);
                        String operacion;
                        long inicio = System.nanoTime();
                        int codigo;
                        if (dado < 50) {
                            operacion = "busqueda";
                            String q = CONSULTAS[azar.nextInt(CONSULTAS.length)].replace(" ", "%20");
                            String filtros = azar.nextBoolean() ? "" : "&categoria=" + CATEGORIAS[azar.nextInt(4)]
                                    + "&desde=1980&disponibles=true";
                            codigo = get(http, base + "/api/libros?q=" + q + filtros, null);
                        } else if (dado < 65) {
                            operacion = "detalle";
                            codigo = get(http, base + "/api/libros/" + (1 + azar.nextInt(5000)), null);
                        } else if (dado < 90) {
                            operacion = "recomendaciones";
                            codigo = get(http, base + "/api/recomendaciones?n=5", token);
                        } else {
                            operacion = "prestamo";
                            codigo = prestarYDevolver(http, base, token, 1 + azar.nextInt(5000));
                        }
                        if (inicio > calentamiento) {
                            mediciones.add(new Medicion(operacion, (System.nanoTime() - inicio) / 1000, codigo));
                        }
                        estado.merge(operacion, 1L, Long::sum);
                    }
                    terminados.countDown();
                    return null;
                });
            }
            terminados.await();
        }
        return mediciones;
    }

    private static int prestarYDevolver(HttpClient http, String base, String token, int libro) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/api/prestamos"))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"libroId\":" + libro + "}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 201) {
                Matcher m = ID.matcher(r.body());
                if (m.find()) {
                    http.send(HttpRequest.newBuilder(URI.create(base + "/api/prestamos/" + m.group(1) + "/devolucion"))
                            .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token)
                            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
                }
            }
            return r.statusCode();
        } catch (Exception e) {
            return 0;
        }
    }

    private static int get(HttpClient http, String url, String token) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET();
            if (token != null) {
                b.header("Authorization", "Bearer " + token);
            }
            return http.send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) {
            return 0;
        }
    }

    private static double pct(List<Long> ordenados, int percentil) {
        if (ordenados.isEmpty()) {
            return 0;
        }
        int indice = (int) Math.ceil(percentil / 100.0 * ordenados.size()) - 1;
        return ordenados.get(Math.max(0, indice)) / 1000.0;
    }
}

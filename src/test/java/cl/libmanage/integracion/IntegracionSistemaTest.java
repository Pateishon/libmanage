package cl.libmanage.integracion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cl.libmanage.dominio.Rol;
import cl.libmanage.dominio.Usuario;
import cl.libmanage.soporte.ClienteHttp;
import cl.libmanage.soporte.ClienteHttp.Respuesta;
import cl.libmanage.soporte.Escenario;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Pruebas de integración y de sistema: la interfaz web, la API, los servicios y el almacenamiento
 * funcionando juntos a través de HTTP real.
 */
public class IntegracionSistemaTest {
    private Escenario e;
    private ClienteHttp api;

    @Before
    public void iniciar() throws Exception {
        e = new Escenario();
        api = e.iniciarServidor();
    }

    @After
    public void detener() {
        e.detener();
    }

    private static List<?> parsearLista(String json) {
        return (List<?>) cl.libmanage.web.Json.leerObjeto("{\"x\":" + json + "}").get("x");
    }

    private String ingresar(String email) {
        Respuesta r = api.post("/api/login", null, "{\"email\":\"" + email + "\",\"contrasena\":\"" + Escenario.CLAVE + "\"}");
        assertEquals(200, r.estado());
        return (String) r.json().get("token");
    }

    @Test
    public void flujoCompletoDeUnEstudiante() {
        String token = ingresar("ana@u.cl");
        // 1. Busca y encuentra el libro con su disponibilidad.
        Respuesta busqueda = api.get("/api/libros?q=codigo%20limpio", null);
        Map<?, ?> libro = (Map<?, ?>) ((List<?>) busqueda.json().get("libros")).get(0);
        assertEquals(2L, libro.get("disponibles"));
        // 2. Pide el préstamo: el inventario baja y la búsqueda lo refleja.
        Respuesta prestamo = api.post("/api/prestamos", token, "{\"libroId\":" + libro.get("id") + "}");
        assertEquals(201, prestamo.estado());
        assertEquals("2026-11-09", prestamo.json().get("limite"));
        Map<?, ?> actualizado = (Map<?, ?>) ((List<?>) api.get("/api/libros?q=codigo%20limpio", null).json().get("libros")).get(0);
        assertEquals(1L, actualizado.get("disponibles"));
        // 3. Lo ve en "Mis préstamos" y ya no aparece entre sus recomendaciones.
        List<?> mios = parsearLista(api.get("/api/prestamos", token).cuerpo());
        assertEquals(1, mios.size());
        assertEquals("ACTIVO", ((Map<?, ?>) mios.get(0)).get("estado"));
        String recomendaciones = api.get("/api/recomendaciones?n=20", token).cuerpo();
        assertFalse(recomendaciones.contains("Código limpio"));
        // 4. Pasan 10 días: el préstamo aparece vencido y bloquea nuevos préstamos.
        e.reloj.avanzar(Duration.ofDays(10));
        token = ingresar("ana@u.cl");
        assertEquals("VENCIDO", ((Map<?, ?>) parsearLista(api.get("/api/prestamos", token).cuerpo()).get(0)).get("estado"));
        assertEquals(409, api.post("/api/prestamos", token, "{\"libroId\":1}").estado());
        // 5. Devuelve: el ejemplar vuelve al inventario y puede pedir otro libro.
        long id = (Long) ((Map<?, ?>) mios.get(0)).get("id");
        assertEquals(200, api.post("/api/prestamos/" + id + "/devolucion", token, "").estado());
        assertEquals(2L, api.get("/api/libros/" + libro.get("id"), null).json().get("disponibles"));
        assertEquals(201, api.post("/api/prestamos", token, "{\"libroId\":1}").estado());
    }

    @Test
    public void libroAgregadoPorAdministradorQuedaDisponibleParaLosUsuarios() {
        String admin = ingresar("admin@u.cl");
        Respuesta creado = api.post("/api/admin/libros", admin, "{\"isbn\":\"9780134685991\",\"titulo\":\"Effective Java\","
                + "\"autor\":\"Joshua Bloch\",\"categoria\":\"Informática\",\"anio\":2018,\"ejemplares\":1}");
        assertEquals(201, creado.estado());
        assertEquals(1L, api.get("/api/libros?q=effective", null).json().get("total"));
        String ana = ingresar("ana@u.cl");
        assertEquals(201, api.post("/api/prestamos", ana, "{\"libroId\":" + creado.json().get("id") + "}").estado());
        Object id = creado.json().get("id");
        assertEquals(409, api.delete("/api/admin/libros/" + id, admin).estado());  // tiene un préstamo activo
        Respuesta ampliado = api.put("/api/admin/libros/" + id + "/ejemplares", admin, "{\"ejemplares\":3}");
        assertEquals(2L, ampliado.json().get("disponibles"));
    }

    @Test
    public void cincuentaUsuariosConcurrentesCompitenPorDosEjemplares() throws Exception {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Usuario u = e.usuarios.guardar(new Usuario(0, "Lector " + i, "lector" + i + "@u.cl", "1-9",
                    e.contrasenas.hash(Escenario.CLAVE), Rol.ESTUDIANTE, true));
            tokens.add(e.token(u));
        }
        AtomicInteger exitos = new AtomicInteger();
        AtomicInteger conflictos = new AtomicInteger();
        CountDownLatch partida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();
        for (String token : tokens) {
            hilos.submit(() -> {
                partida.await();
                int estado = api.post("/api/prestamos", token, "{\"libroId\":2}").estado();
                (estado == 201 ? exitos : conflictos).incrementAndGet();
                return null;
            });
        }
        partida.countDown();
        hilos.shutdown();
        assertTrue(hilos.awaitTermination(20, TimeUnit.SECONDS));
        assertEquals(2, exitos.get());
        assertEquals(48, conflictos.get());
        assertEquals(0L, api.get("/api/libros/2", null).json().get("disponibles"));
    }

    @Test
    public void laInterfazWebSeSirveYUsaLaApi() {
        Respuesta html = api.get("/", null);
        assertEquals(200, html.estado());
        assertTrue(html.cuerpo().contains("<script src=\"/app.js\""));
        Respuesta js = api.get("/app.js", null);
        assertEquals(200, js.estado());
        assertTrue(js.cuerpo().contains("/api/libros"));
        assertFalse("la interfaz no debe usar innerHTML", js.cuerpo().contains("innerHTML ="));
        assertEquals(200, api.get("/estilos.css", null).estado());
    }

    @Test
    public void saludDelSistema() {
        assertEquals("ok", api.get("/api/salud", null).json().get("estado"));
        assertTrue(parsearLista(api.get("/api/recomendaciones", ingresar("luis@u.cl")).cuerpo()).size() > 0);
    }
}

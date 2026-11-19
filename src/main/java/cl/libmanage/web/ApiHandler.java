package cl.libmanage.web;

import cl.libmanage.dominio.ErrorNegocio;
import cl.libmanage.dominio.Libro;
import cl.libmanage.dominio.Prestamo;
import cl.libmanage.dominio.Usuario;
import cl.libmanage.seguridad.Enmascarador;
import cl.libmanage.seguridad.TokenService.Sesion;
import cl.libmanage.servicio.AutenticacionService.Ingreso;
import cl.libmanage.servicio.CriteriosBusqueda;
import cl.libmanage.servicio.ResultadoBusqueda;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** API REST de LibManage. Toda respuesta lleva cabeceras de seguridad y los errores no exponen detalles internos. */
public final class ApiHandler implements HttpHandler {
    private static final Logger LOG = Logger.getLogger(ApiHandler.class.getName());
    private static final int MAX_CUERPO = 16 * 1024;
    private static final Pattern LIBRO = Pattern.compile("/api/libros/(\\d{1,18})");
    private static final Pattern ADMIN_LIBRO = Pattern.compile("/api/admin/libros/(\\d{1,18})");
    private static final Pattern ADMIN_EJEMPLARES = Pattern.compile("/api/admin/libros/(\\d{1,18})/ejemplares");
    private static final Pattern DEVOLUCION = Pattern.compile("/api/prestamos/(\\d{1,18})/devolucion");

    private final Aplicacion app;
    private final Clock reloj;

    public ApiHandler(Aplicacion app, Clock reloj) {
        this.app = app;
        this.reloj = reloj;
    }

    private record Respuesta(int estado, Object cuerpo) {
    }

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        Respuesta respuesta;
        try {
            respuesta = enrutar(intercambio);
        } catch (ErrorNegocio e) {
            respuesta = new Respuesta(e.estado(), Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            respuesta = new Respuesta(400, Map.of("error", "Solicitud inválida"));
        } catch (RuntimeException e) {
            String referencia = UUID.randomUUID().toString();
            LOG.log(Level.SEVERE, "Error interno " + referencia, e);
            respuesta = new Respuesta(500, Map.of("error", "Error interno", "referencia", referencia));
        }
        enviar(intercambio, respuesta);
    }

    private Respuesta enrutar(HttpExchange ex) throws IOException {
        String metodo = ex.getRequestMethod();
        String ruta = ex.getRequestURI().getPath();
        Map<String, String> query = parametros(ex.getRequestURI().getRawQuery());
        Matcher m;

        if (ruta.equals("/api/salud")) {
            return new Respuesta(200, Map.of("estado", "ok"));
        }
        if (ruta.equals("/api/login")) {
            exigirMetodo(metodo, "POST");
            Map<String, Object> cuerpo = leerCuerpo(ex);
            Ingreso ingreso = app.autenticacion.ingresar(texto(cuerpo, "email"), texto(cuerpo, "contrasena"));
            return new Respuesta(200, Map.of("token", ingreso.token(), "nombre", ingreso.usuario().nombre(),
                    "rol", ingreso.usuario().rol().name()));
        }
        if (ruta.equals("/api/libros")) {
            exigirMetodo(metodo, "GET");
            return new Respuesta(200, resultado(app.busqueda.buscar(criterios(query))));
        }
        if ((m = LIBRO.matcher(ruta)).matches()) {
            exigirMetodo(metodo, "GET");
            Libro libro = app.libros.porId(Long.parseLong(m.group(1)))
                    .orElseThrow(() -> new ErrorNegocio(404, "Libro no encontrado"));
            return new Respuesta(200, libro(libro));
        }
        if (ruta.equals("/api/admin/libros")) {
            exigirMetodo(metodo, "POST");
            exigirAdmin(ex);
            Map<String, Object> c = leerCuerpo(ex);
            Libro nuevo = app.inventario.agregar(texto(c, "isbn"), texto(c, "titulo"), texto(c, "autor"),
                    texto(c, "categoria"), entero(c, "anio"), entero(c, "ejemplares"));
            return new Respuesta(201, libro(nuevo));
        }
        if ((m = ADMIN_EJEMPLARES.matcher(ruta)).matches()) {
            exigirMetodo(metodo, "PUT");
            exigirAdmin(ex);
            int total = entero(leerCuerpo(ex), "ejemplares");
            return new Respuesta(200, libro(app.inventario.ajustarEjemplares(Long.parseLong(m.group(1)), total)));
        }
        if ((m = ADMIN_LIBRO.matcher(ruta)).matches()) {
            exigirMetodo(metodo, "DELETE");
            exigirAdmin(ex);
            app.inventario.darDeBaja(Long.parseLong(m.group(1)));
            return new Respuesta(200, Map.of("eliminado", true));
        }
        if (ruta.equals("/api/prestamos")) {
            Sesion sesion = exigirSesion(ex);
            if (metodo.equals("GET")) {
                long usuarioId = sesion.usuarioId();
                if (query.containsKey("usuario")) {
                    if (!sesion.esAdmin()) {
                        throw new ErrorNegocio(403, "Solo un administrador puede ver préstamos de otros usuarios");
                    }
                    usuarioId = Long.parseLong(query.get("usuario"));
                }
                return new Respuesta(200, prestamos(app.prestamosService.prestamosDe(usuarioId)));
            }
            exigirMetodo(metodo, "POST");
            long libroId = entero(leerCuerpo(ex), "libroId");
            return new Respuesta(201, prestamo(app.prestamosService.prestar(sesion.usuarioId(), libroId)));
        }
        if ((m = DEVOLUCION.matcher(ruta)).matches()) {
            exigirMetodo(metodo, "POST");
            Sesion sesion = exigirSesion(ex);
            return new Respuesta(200, prestamo(app.prestamosService.devolver(Long.parseLong(m.group(1)),
                    sesion.usuarioId(), sesion.esAdmin())));
        }
        if (ruta.equals("/api/recomendaciones")) {
            exigirMetodo(metodo, "GET");
            Sesion sesion = exigirSesion(ex);
            int n = enteroQuery(query, "n", 5);
            return new Respuesta(200, app.recomendaciones.recomendar(sesion.usuarioId(), n).stream()
                    .map(ApiHandler::libro).toList());
        }
        if (ruta.equals("/api/usuarios/yo")) {
            exigirMetodo(metodo, "GET");
            Usuario u = app.usuarios.porId(exigirSesion(ex).usuarioId())
                    .orElseThrow(() -> new ErrorNegocio(404, "Usuario no encontrado"));
            Map<String, Object> datos = new LinkedHashMap<>();
            datos.put("id", u.id());
            datos.put("nombre", u.nombre());
            datos.put("email", Enmascarador.email(u.email()));
            datos.put("rut", Enmascarador.rut(u.rut()));
            datos.put("rol", u.rol().name());
            return new Respuesta(200, datos);
        }
        throw new ErrorNegocio(404, "Recurso no encontrado");
    }

    // ---------- autenticación ----------
    private Sesion exigirSesion(HttpExchange ex) {
        String cabecera = ex.getRequestHeaders().getFirst("Authorization");
        Optional<Sesion> sesion = cabecera != null && cabecera.startsWith("Bearer ")
                ? app.tokens.validar(cabecera.substring(7)) : Optional.empty();
        return sesion.orElseThrow(() -> new ErrorNegocio(401, "Debe iniciar sesión"));
    }

    private void exigirAdmin(HttpExchange ex) {
        if (!exigirSesion(ex).esAdmin()) {
            throw new ErrorNegocio(403, "Requiere rol de administrador");
        }
    }

    private static void exigirMetodo(String metodo, String esperado) {
        if (!metodo.equals(esperado)) {
            throw new ErrorNegocio(405, "Método no permitido");
        }
    }

    // ---------- entrada ----------
    private static Map<String, Object> leerCuerpo(HttpExchange ex) throws IOException {
        try (InputStream entrada = ex.getRequestBody()) {
            byte[] datos = entrada.readNBytes(MAX_CUERPO + 1);
            if (datos.length > MAX_CUERPO) {
                throw new ErrorNegocio(413, "El cuerpo de la solicitud es demasiado grande");
            }
            return Json.leerObjeto(new String(datos, StandardCharsets.UTF_8));
        }
    }

    private static Map<String, String> parametros(String crudo) {
        Map<String, String> mapa = new HashMap<>();
        if (crudo == null || crudo.isEmpty()) {
            return mapa;
        }
        for (String par : crudo.split("&")) {
            int igual = par.indexOf('=');
            String clave = URLDecoder.decode(igual < 0 ? par : par.substring(0, igual), StandardCharsets.UTF_8);
            String valor = igual < 0 ? "" : URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8);
            mapa.put(clave, valor);
        }
        return mapa;
    }

    private static CriteriosBusqueda criterios(Map<String, String> q) {
        CriteriosBusqueda.Orden orden;
        try {
            orden = CriteriosBusqueda.Orden.valueOf(q.getOrDefault("orden", "RELEVANCIA").toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ErrorNegocio(400, "Orden inválido");
        }
        return new CriteriosBusqueda(q.get("q"), q.get("autor"), q.get("categoria"), enteroOpcional(q, "desde"),
                enteroOpcional(q, "hasta"), "true".equalsIgnoreCase(q.get("disponibles")), orden,
                enteroQuery(q, "pagina", 1), enteroQuery(q, "tamano", 20));
    }

    private static Integer enteroOpcional(Map<String, String> q, String clave) {
        String valor = q.get(clave);
        return valor == null || valor.isBlank() ? null : enteroQuery(q, clave, 0);
    }

    private static int enteroQuery(Map<String, String> q, String clave, int defecto) {
        String valor = q.get(clave);
        if (valor == null || valor.isBlank()) {
            return defecto;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            throw new ErrorNegocio(400, "El parámetro " + clave + " debe ser un número entero");
        }
    }

    private static String texto(Map<String, Object> cuerpo, String clave) {
        Object valor = cuerpo.get(clave);
        if (valor != null && !(valor instanceof String)) {
            throw new ErrorNegocio(400, "El campo " + clave + " debe ser texto");
        }
        return (String) valor;
    }

    private static int entero(Map<String, Object> cuerpo, String clave) {
        Object valor = cuerpo.get(clave);
        if (!(valor instanceof Long numero) || numero > Integer.MAX_VALUE || numero < Integer.MIN_VALUE) {
            throw new ErrorNegocio(400, "El campo " + clave + " debe ser un número entero");
        }
        return numero.intValue();
    }

    // ---------- salida ----------
    static Map<String, Object> libro(Libro l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.id());
        m.put("isbn", l.isbn());
        m.put("titulo", l.titulo());
        m.put("autor", l.autor());
        m.put("categoria", l.categoria());
        m.put("anio", l.anio());
        m.put("ejemplares", l.ejemplares());
        m.put("disponibles", l.disponibles());
        return m;
    }

    private static Map<String, Object> resultado(ResultadoBusqueda r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", r.total());
        m.put("pagina", r.pagina());
        m.put("tamano", r.tamano());
        m.put("libros", r.libros().stream().map(ApiHandler::libro).toList());
        return m;
    }

    private List<Map<String, Object>> prestamos(List<Prestamo> lista) {
        return lista.stream().map(this::prestamo).toList();
    }

    private Map<String, Object> prestamo(Prestamo p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("libroId", p.libroId());
        m.put("titulo", app.libros.porId(p.libroId()).map(Libro::titulo).orElse("(libro dado de baja)"));
        m.put("inicio", p.inicio().toString());
        m.put("limite", p.limite().toString());
        m.put("devolucion", p.devolucion() == null ? null : p.devolucion().toString());
        m.put("estado", !p.activo() ? "DEVUELTO" : p.vencido(LocalDate.now(reloj)) ? "VENCIDO" : "ACTIVO");
        return m;
    }

    private static void enviar(HttpExchange ex, Respuesta respuesta) throws IOException {
        byte[] cuerpo = Json.escribir(respuesta.cuerpo()).getBytes(StandardCharsets.UTF_8);
        CabecerasSeguridad.aplicar(ex.getResponseHeaders());
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(respuesta.estado(), cuerpo.length);
        try (OutputStream salida = ex.getResponseBody()) {
            salida.write(cuerpo);
        }
    }
}

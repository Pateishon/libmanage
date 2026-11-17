package cl.libmanage.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import cl.libmanage.dominio.Libro;
import cl.libmanage.repositorio.LibroRepositorio;
import cl.libmanage.repositorio.LibroRepositorioMemoria;
import cl.libmanage.soporte.ClienteHttp;
import cl.libmanage.soporte.ClienteHttp.Respuesta;
import cl.libmanage.soporte.Escenario;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Pruebas de seguridad de la API, alineadas con OWASP Top 10 (2021). */
public class SeguridadApiTest {
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

    private Respuesta login(String email, String clave) {
        return api.post("/api/login", null, "{\"email\":\"" + email + "\",\"contrasena\":\"" + clave + "\"}");
    }

    // A01 Control de acceso
    @Test
    public void rutasProtegidasExigenSesion() {
        for (String ruta : List.of("/api/prestamos", "/api/recomendaciones", "/api/usuarios/yo")) {
            assertEquals(ruta, 401, api.get(ruta, null).estado());
            assertEquals(ruta, 401, api.get(ruta, "token-inventado").estado());
        }
    }

    @Test
    public void estudianteNoAccedeAFuncionesDeAdministrador() {
        String ana = e.token(e.estudiante);
        assertEquals(403, api.post("/api/admin/libros", ana, "{}").estado());
        assertEquals(403, api.put("/api/admin/libros/1/ejemplares", ana, "{\"ejemplares\":9}").estado());
        assertEquals(403, api.delete("/api/admin/libros/1", ana).estado());
        assertTrue(e.libros.porId(1).isPresent());
    }

    @Test
    public void noSePuedenVerNiDevolverPrestamosAjenos() {
        Respuesta delDocente = api.post("/api/prestamos", e.token(e.docente), "{\"libroId\":1}");
        long prestamo = (Long) delDocente.json().get("id");
        String ana = e.token(e.estudiante);
        assertEquals(403, api.get("/api/prestamos?usuario=" + e.docente.id(), ana).estado());
        assertEquals(404, api.post("/api/prestamos/" + prestamo + "/devolucion", ana, "").estado());
        assertEquals(200, api.get("/api/prestamos?usuario=" + e.docente.id(), e.token(e.admin)).estado());
    }

    @Test
    public void tokenExpiradoEsRechazado() {
        String ana = e.token(e.estudiante);
        assertEquals(200, api.get("/api/usuarios/yo", ana).estado());
        e.reloj.avanzar(Duration.ofMinutes(31));
        assertEquals(401, api.get("/api/usuarios/yo", ana).estado());
    }

    // A07 Fallas de identificación y autenticación
    @Test
    public void loginNoRevelaSiElEmailExiste() {
        Respuesta inexistente = login("nadie@u.cl", "x");
        Respuesta claveMala = login("ana@u.cl", "x");
        assertEquals(401, inexistente.estado());
        assertEquals(inexistente.cuerpo(), claveMala.cuerpo());
        assertEquals(200, login("ana@u.cl", Escenario.CLAVE).estado());
        assertEquals(401, login("bruno@u.cl", Escenario.CLAVE).estado());  // usuario bloqueado
    }

    @Test
    public void bloqueoPorFuerzaBruta() {
        for (int i = 0; i < 5; i++) {
            assertEquals(401, login("ana@u.cl", "intento" + i).estado());
        }
        assertEquals("con la clave correcta también queda bloqueado", 429, login("ana@u.cl", Escenario.CLAVE).estado());
        e.reloj.avanzar(Duration.ofMinutes(16));
        assertEquals(200, login("ana@u.cl", Escenario.CLAVE).estado());
    }

    // A02 Fallas criptográficas / protección de datos personales
    @Test
    public void datosPersonalesSeMuestranEnmascarados() {
        Respuesta yo = api.get("/api/usuarios/yo", e.token(e.estudiante));
        assertEquals("a***@u.cl", yo.json().get("email"));
        assertEquals("**.***.678-5", yo.json().get("rut"));
        assertFalse(yo.cuerpo().contains("12.345.678-5"));
        assertFalse(yo.cuerpo().contains("pbkdf2"));
    }

    // A03 Inyección
    @Test
    public void entradasMaliciosasNoAlteranElSistema() {
        String[] cargas = {"' OR '1'='1", "1; DROP TABLE libros; --", "<script>alert(1)</script>", "../../etc/passwd",
            "%00", "${jndi:ldap://x}"};
        for (String carga : cargas) {
            Respuesta r = api.get("/api/libros?q=" + URLEncoder.encode(carga, StandardCharsets.UTF_8), null);
            assertEquals(carga, 200, r.estado());
            assertFalse(carga, r.cuerpo().contains("<script>"));
        }
        assertEquals(8, e.libros.todos().size());
    }

    @Test
    public void textoMaliciosoGuardadoSeDevuelveEscapado() {
        e.app.busqueda.indexar(e.libro("<img src=x onerror=alert(1)>", "Atacante", "Literatura", 2020, 1));
        Respuesta r = api.get("/api/libros?q=atacante", null);
        assertFalse(r.cuerpo().contains("<img"));
        assertTrue(r.cuerpo().contains("\\u003cimg"));
    }

    @Test
    public void cuerposGrandesOMalFormadosSeRechazan() {
        String ana = e.token(e.estudiante);
        assertEquals(413, api.post("/api/prestamos", ana, "{\"x\":\"" + "a".repeat(20_000) + "\"}").estado());
        assertEquals(400, api.post("/api/login", null, "{\"email\":").estado());
        assertEquals(400, api.post("/api/login", null, "{\"email\":5,\"contrasena\":\"x\"}").estado());
    }

    // A05 Configuración de seguridad incorrecta
    @Test
    public void cabecerasDeSeguridadEnTodasLasRespuestas() {
        for (Respuesta r : List.of(api.get("/api/salud", null), api.get("/api/no-existe", null),
                api.get("/", null), api.get("/app.js", null))) {
            assertEquals("nosniff", r.cabecera("X-Content-Type-Options"));
            assertEquals("DENY", r.cabecera("X-Frame-Options"));
            assertNotNull(r.cabecera("Content-Security-Policy"));
            assertTrue(r.cabecera("Content-Security-Policy").contains("frame-ancestors 'none'"));
        }
        assertEquals("no-store", api.get("/api/usuarios/yo", e.token(e.estudiante)).cabecera("Cache-Control"));
    }

    @Test
    public void archivosEstaticosNoPermitenRecorrerDirectorios() {
        assertEquals(404, api.get("/../../etc/passwd", null).estado());
        assertEquals(404, api.get("/static/app.js", null).estado());
        assertEquals(404, api.get("/cl/libmanage/web/Servidor.class", null).estado());
    }

    @Test
    public void erroresInternosNoExponenDetalles() throws Exception {
        LibroRepositorioMemoria real = new LibroRepositorioMemoria();
        LibroRepositorio defectuoso = new LibroRepositorio() {
            @Override
            public Libro guardar(Libro libro) {
                return real.guardar(libro);
            }

            @Override
            public Optional<Libro> porId(long id) {
                throw new IllegalStateException("SQLException: tabla libros, usuario root, clave 1234");
            }

            @Override
            public Optional<Libro> porIsbn(String isbn) {
                return real.porIsbn(isbn);
            }

            @Override
            public List<Libro> todos() {
                return real.todos();
            }

            @Override
            public boolean ajustarDisponibles(long id, int delta) {
                return real.ajustarDisponibles(id, delta);
            }

            @Override
            public void eliminar(long id) {
                real.eliminar(id);
            }
        };
        Escenario conFalla = new Escenario(defectuoso);
        try {
            Respuesta r = conFalla.iniciarServidor().get("/api/libros/1", null);
            assertEquals(500, r.estado());
            assertFalse(r.cuerpo().contains("SQL"));
            assertFalse(r.cuerpo().contains("root"));
            assertFalse(r.cuerpo().contains("at cl.libmanage"));
            assertNotNull(r.json().get("referencia"));
        } finally {
            conFalla.detener();
        }
    }
}

package cl.libmanage.integracion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import cl.libmanage.dominio.Libro;
import cl.libmanage.repositorio.JdbcLibroRepositorio;
import cl.libmanage.servicio.CriteriosBusqueda;
import cl.libmanage.soporte.ClienteHttp;
import cl.libmanage.soporte.Escenario;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;

/**
 * Integración backend ↔ MySQL. Se ejecuta en GitHub Actions contra un contenedor MySQL 8 real
 * (variables LIBMANAGE_TEST_DB_*). Sin esas variables, las pruebas se omiten.
 */
public class JdbcLibroRepositorioIT {
    private static final String URL = System.getenv("LIBMANAGE_TEST_DB_URL");
    private static final String USUARIO = System.getenv("LIBMANAGE_TEST_DB_USUARIO");
    private static final String CLAVE = System.getenv("LIBMANAGE_TEST_DB_CLAVE");
    private JdbcLibroRepositorio repo;

    @Before
    public void preparar() throws Exception {
        assumeTrue("MySQL no configurado: prueba omitida", URL != null);
        try (Connection c = DriverManager.getConnection(URL, USUARIO, CLAVE); Statement s = c.createStatement()) {
            for (String sentencia : esquema().split(";")) {
                if (!sentencia.isBlank() && !sentencia.strip().startsWith("--")) {
                    s.execute(sentencia);
                }
            }
            s.execute("SET FOREIGN_KEY_CHECKS = 0");
            s.execute("TRUNCATE TABLE prestamos");
            s.execute("TRUNCATE TABLE libros");
            s.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
        repo = new JdbcLibroRepositorio(URL, USUARIO, CLAVE);
    }

    private static String esquema() throws IOException {
        try (InputStream in = JdbcLibroRepositorioIT.class.getResourceAsStream("/db/schema.sql")) {
            StringBuilder sinComentarios = new StringBuilder();
            for (String linea : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!linea.strip().startsWith("--")) {
                    sinComentarios.append(linea).append('\n');
                }
            }
            return sinComentarios.toString();
        }
    }

    @Test
    public void guardaConsultaYActualizaEnMySql() {
        Libro guardado = repo.guardar(new Libro(0, "9780134685991", "Effective Java", "Joshua Bloch", "Informática",
                2018, 3, 3));
        assertTrue(guardado.id() > 0);
        assertEquals("Effective Java", repo.porIsbn("9780134685991").orElseThrow().titulo());
        repo.guardar(guardado.conEjemplares(5));
        assertEquals(5, repo.porId(guardado.id()).orElseThrow().ejemplares());
        repo.eliminar(guardado.id());
        assertTrue(repo.porId(guardado.id()).isEmpty());
    }

    @Test
    public void laBaseRechazaDisponiblesFueraDeRango() {
        Libro libro = repo.guardar(new Libro(0, "9780134685991", "Effective Java", "Joshua Bloch", "Informática",
                2018, 1, 1));
        assertFalse(repo.ajustarDisponibles(libro.id(), 1));   // superaría los ejemplares
        assertTrue(repo.ajustarDisponibles(libro.id(), -1));
        assertFalse(repo.ajustarDisponibles(libro.id(), -1));  // quedaría negativo
        try {
            repo.guardar(libro.conDisponibles(-5));
            fail("La restricción CHECK de MySQL debió rechazar el valor");
        } catch (IllegalStateException esperado) {
            // chk_disponibles
        }
    }

    @Test
    public void ajusteConcurrenteEsAtomicoEnLaBase() throws Exception {
        Libro libro = repo.guardar(new Libro(0, "9780134685991", "Effective Java", "Joshua Bloch", "Informática",
                2018, 2, 2));
        AtomicInteger exitos = new AtomicInteger();
        CountDownLatch partida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(20);
        for (int i = 0; i < 20; i++) {
            hilos.submit(() -> {
                partida.await();
                if (repo.ajustarDisponibles(libro.id(), -1)) {
                    exitos.incrementAndGet();
                }
                return null;
            });
        }
        partida.countDown();
        hilos.shutdown();
        assertTrue(hilos.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(2, exitos.get());
        assertEquals(0, repo.porId(libro.id()).orElseThrow().disponibles());
    }

    @Test
    public void inyeccionSqlNoTieneEfecto() {
        repo.guardar(new Libro(0, "9780134685991", "'); DROP TABLE libros; --", "Robert'); --", "Informática",
                2018, 1, 1));
        assertTrue(repo.porIsbn("' OR '1'='1").isEmpty());
        assertTrue(repo.porIsbn("9780134685991' OR '1'='1").isEmpty());
        assertEquals("'); DROP TABLE libros; --", repo.todos().get(0).titulo());  // se guardó como texto literal
    }

    @Test
    public void sistemaCompletoSobreMySql() throws Exception {
        Escenario e = new Escenario(repo);
        try {
            ClienteHttp api = e.iniciarServidor();
            assertEquals(1, e.app.busqueda.buscar(CriteriosBusqueda.texto("sommerville")).total());
            assertEquals(201, api.post("/api/prestamos", e.token(e.estudiante), "{\"libroId\":1}").estado());
            assertEquals(2, repo.porId(1).orElseThrow().disponibles());  // el préstamo quedó en MySQL
        } finally {
            e.detener();
        }
    }

    @Test
    public void usuarioDeAplicacionTieneMinimoPrivilegioYExigeTls() throws SQLException {
        String urlApp = System.getenv("LIBMANAGE_TEST_DB_APP_URL");
        assumeTrue("Usuario de aplicación no configurado", urlApp != null);
        String usuarioApp = System.getenv("LIBMANAGE_TEST_DB_APP_USUARIO");
        String claveApp = System.getenv("LIBMANAGE_TEST_DB_APP_CLAVE");
        try (Connection c = DriverManager.getConnection(urlApp, usuarioApp, claveApp); Statement s = c.createStatement()) {
            s.executeQuery("SELECT COUNT(*) FROM libros").close();
            try {
                s.execute("DROP TABLE libros");
                fail("El usuario de la aplicación no debe poder borrar tablas");
            } catch (SQLException esperado) {
                // sin privilegio DROP
            }
        }
        try {
            DriverManager.getConnection(urlApp.replace("sslMode=REQUIRED", "sslMode=DISABLED"), usuarioApp, claveApp).close();
            fail("Una conexión sin TLS debe ser rechazada (REQUIRE SSL)");
        } catch (SQLException esperado) {
            // conexión sin cifrar rechazada
        }
    }
}

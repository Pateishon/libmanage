package cl.libmanage.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cl.libmanage.soporte.ClienteHttp;
import cl.libmanage.soporte.ClienteHttp.Respuesta;
import cl.libmanage.soporte.Escenario;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Pruebas de caja negra sobre la API HTTP: solo se usan entradas y salidas públicas.
 * Técnicas: particiones de equivalencia, análisis de valores límite y tabla de decisión.
 */
public class ApiCajaNegraTest {
    private Escenario e;
    private ClienteHttp api;
    private String tokenAna;

    @Before
    public void iniciar() throws Exception {
        e = new Escenario();
        api = e.iniciarServidor();
        tokenAna = e.token(e.estudiante);
    }

    @After
    public void detener() {
        e.detener();
    }

    private int estadoBusqueda(String query) {
        return api.get("/api/libros?" + query, null).estado();
    }

    @Test
    public void busquedaValoresLimiteDelTamanoDePagina() {
        assertEquals(400, estadoBusqueda("tamano=0"));
        assertEquals(200, estadoBusqueda("tamano=1"));
        assertEquals(200, estadoBusqueda("tamano=50"));
        assertEquals(400, estadoBusqueda("tamano=51"));
        assertEquals(400, estadoBusqueda("pagina=0"));
        assertEquals(200, estadoBusqueda("pagina=1"));
    }

    @Test
    public void busquedaValoresLimiteDeAnios() {
        assertEquals(400, estadoBusqueda("desde=1449"));
        assertEquals(200, estadoBusqueda("desde=1450"));
        assertEquals(200, estadoBusqueda("hasta=2100"));
        assertEquals(400, estadoBusqueda("hasta=2101"));
        assertEquals(200, estadoBusqueda("desde=2000&hasta=2000"));
        assertEquals(400, estadoBusqueda("desde=2001&hasta=2000"));
    }

    @Test
    public void busquedaParticionesInvalidas() {
        assertEquals(400, estadoBusqueda("tamano=abc"));
        assertEquals(400, estadoBusqueda("desde=20.5"));
        assertEquals(400, estadoBusqueda("orden=PRECIO"));
        assertEquals(200, estadoBusqueda("q=" + "a".repeat(100)));
        assertEquals(400, estadoBusqueda("q=" + "a".repeat(101)));
    }

    @Test
    public void busquedaAvanzadaCombinaFiltros() {
        Respuesta r = api.get("/api/libros?q=software&categoria=Inform%C3%A1tica&desde=2010&hasta=2011&orden=TITULO", null);
        assertEquals(200, r.estado());
        assertEquals(1L, r.json().get("total"));
        assertEquals("Ingeniería de software", ((Map<?, ?>) ((List<?>) r.json().get("libros")).get(0)).get("titulo"));
    }

    @Test
    public void detalleDeLibroExistenteEInexistente() {
        assertEquals(200, api.get("/api/libros/1", null).estado());
        assertEquals(404, api.get("/api/libros/999", null).estado());
        assertEquals(404, api.get("/api/libros/abc", null).estado());
    }

    /**
     * Tabla de decisión del préstamo (condiciones → resultado esperado):
     * sesión válida | libro existe | hay ejemplares | bajo el límite → código HTTP.
     */
    @Test
    public void tablaDeDecisionDelPrestamo() {
        assertEquals("R1 sin sesión", 401, api.post("/api/prestamos", null, "{\"libroId\":1}").estado());
        assertEquals("R2 libro inexistente", 404, api.post("/api/prestamos", tokenAna, "{\"libroId\":999}").estado());
        api.post("/api/prestamos", e.token(e.docente), "{\"libroId\":3}");
        assertEquals("R3 sin ejemplares", 409, api.post("/api/prestamos", tokenAna, "{\"libroId\":3}").estado());
        assertEquals("R5 éxito", 201, api.post("/api/prestamos", tokenAna, "{\"libroId\":1}").estado());
        api.post("/api/prestamos", tokenAna, "{\"libroId\":2}");
        api.post("/api/prestamos", tokenAna, "{\"libroId\":4}");
        assertEquals("R4 sobre el límite", 409, api.post("/api/prestamos", tokenAna, "{\"libroId\":5}").estado());
    }

    @Test
    public void prestamoConCuerpoInvalido() {
        assertEquals(400, api.post("/api/prestamos", tokenAna, "{\"libroId\":\"uno\"}").estado());
        assertEquals(400, api.post("/api/prestamos", tokenAna, "{}").estado());
        assertEquals(400, api.post("/api/prestamos", tokenAna, "no es json").estado());
        assertEquals(400, api.post("/api/prestamos", tokenAna, "{\"libroId\":99999999999}").estado());
    }

    @Test
    public void metodoYRutaNoSoportados() {
        assertEquals(405, api.delete("/api/libros", null).estado());
        assertEquals(404, api.get("/api/no-existe", null).estado());
    }

    @Test
    public void recomendacionesValoresLimite() {
        assertEquals(400, api.get("/api/recomendaciones?n=0", tokenAna).estado());
        assertEquals(200, api.get("/api/recomendaciones?n=1", tokenAna).estado());
        assertEquals(200, api.get("/api/recomendaciones?n=20", tokenAna).estado());
        assertEquals(400, api.get("/api/recomendaciones?n=21", tokenAna).estado());
    }

    @Test
    public void administradorGestionaElInventario() {
        String admin = e.token(e.admin);
        Respuesta creado = api.post("/api/admin/libros", admin, "{\"isbn\":\"978-0-13-468599-1\",\"titulo\":\"Effective Java\","
                + "\"autor\":\"Joshua Bloch\",\"categoria\":\"Informática\",\"anio\":2018,\"ejemplares\":2}");
        assertEquals(201, creado.estado());
        long id = (Long) creado.json().get("id");
        assertEquals(400, api.post("/api/admin/libros", admin, "{\"isbn\":\"123\",\"titulo\":\"X\",\"autor\":\"Y\","
                + "\"categoria\":\"Z\",\"anio\":2018,\"ejemplares\":1}").estado());
        assertEquals(200, api.put("/api/admin/libros/" + id + "/ejemplares", admin, "{\"ejemplares\":5}").estado());
        assertEquals(400, api.put("/api/admin/libros/" + id + "/ejemplares", admin, "{\"ejemplares\":0}").estado());
        assertEquals(200, api.delete("/api/admin/libros/" + id, admin).estado());
        assertEquals(404, api.get("/api/libros/" + id, null).estado());
    }

    @Test
    public void respuestasSiempreSonJson() {
        for (String ruta : List.of("/api/salud", "/api/libros", "/api/no-existe", "/api/libros?tamano=0")) {
            Respuesta r = api.get(ruta, null);
            assertTrue(ruta, r.cabecera("Content-Type").startsWith("application/json"));
            r.json();  // no lanza: cuerpo JSON válido
        }
    }
}

package cl.libmanage.unitarias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cl.libmanage.dominio.ErrorNegocio;
import cl.libmanage.dominio.Libro;
import cl.libmanage.servicio.CriteriosBusqueda;
import cl.libmanage.servicio.CriteriosBusqueda.Orden;
import cl.libmanage.servicio.ResultadoBusqueda;
import cl.libmanage.soporte.Escenario;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/** Búsqueda avanzada: funcionalidad (caja negra) y caminos internos del filtro (caja blanca). */
public class BusquedaServiceTest {
    private Escenario e;

    @Before
    public void preparar() {
        e = new Escenario();
    }

    private ResultadoBusqueda buscar(String texto, String autor, String categoria, Integer desde, Integer hasta,
                                     boolean disponibles, Orden orden, int pagina, int tamano) {
        return e.app.busqueda.buscar(new CriteriosBusqueda(texto, autor, categoria, desde, hasta, disponibles, orden,
                pagina, tamano));
    }

    private static List<String> titulos(ResultadoBusqueda r) {
        return r.libros().stream().map(Libro::titulo).toList();
    }

    @Test
    public void buscaPorPalabraDelTituloSinImportarTildesNiMayusculas() {
        assertEquals(List.of("Código limpio"), titulos(e.app.busqueda.buscar(CriteriosBusqueda.texto("CODIGO"))));
    }

    @Test
    public void buscaPorPrefijoDePalabra() {
        assertEquals(List.of("Refactorización"), titulos(e.app.busqueda.buscar(CriteriosBusqueda.texto("refact"))));
    }

    @Test
    public void variasPalabrasSeCombinanConYLogico() {
        assertEquals(List.of("Ingeniería de software"),
                titulos(e.app.busqueda.buscar(CriteriosBusqueda.texto("software sommerville"))));
        assertEquals(0, e.app.busqueda.buscar(CriteriosBusqueda.texto("software stewart")).total());
    }

    @Test
    public void buscaPorIsbn() {
        Libro libro = e.libros.porId(5).orElseThrow();
        assertEquals(List.of(libro.titulo()), titulos(e.app.busqueda.buscar(CriteriosBusqueda.texto(libro.isbn()))));
    }

    @Test
    public void filtraPorAutorYCategoria() {
        assertEquals(List.of("Refactorización"),
                titulos(buscar(null, "fowler", null, null, null, false, Orden.TITULO, 1, 20)));
        assertEquals(List.of("Álgebra lineal", "Cálculo de una variable"),
                titulos(buscar(null, null, "matematica", null, null, false, Orden.ANIO_DESC, 1, 20)));
    }

    @Test
    public void rangoDeAniosIncluyeAmbosLimites() {
        // Valores límite: 2008 y 2012 deben quedar dentro del rango [2008, 2012].
        assertEquals(List.of("Cálculo de una variable", "Código limpio", "Ingeniería de software"),
                titulos(buscar(null, null, null, 2008, 2012, false, Orden.TITULO, 1, 20)));
        assertEquals(List.of("Código limpio"), titulos(buscar(null, null, null, 2007, 2008, false, Orden.TITULO, 1, 20)));
    }

    @Test
    public void soloDisponiblesExcluyeLibrosSinEjemplares() {
        Libro refactorizacion = e.libros.porId(3).orElseThrow();
        e.libros.ajustarDisponibles(refactorizacion.id(), -1);
        ResultadoBusqueda r = buscar(null, "fowler", null, null, null, true, Orden.TITULO, 1, 20);
        assertEquals(0, r.total());
    }

    @Test
    public void ordenaPorRelevanciaTituloAntesQueAutor() {
        e.app.busqueda.indexar(e.libro("Martin y sus amigos", "Otra Autora", "Literatura", 2000, 1));
        List<String> resultado = titulos(e.app.busqueda.buscar(CriteriosBusqueda.texto("martin")));
        assertEquals("Martin y sus amigos", resultado.get(0));
    }

    @Test
    public void paginaLosResultados() {
        ResultadoBusqueda pagina2 = buscar(null, null, "informatica", null, null, false, Orden.TITULO, 2, 3);
        assertEquals(4, pagina2.total());
        assertEquals(List.of("Refactorización"), titulos(pagina2));
        assertEquals(0, buscar(null, null, "informatica", null, null, false, Orden.TITULO, 3, 3).libros().size());
    }

    @Test
    public void libroNuevoQuedaBuscableAlIndexarse() {
        Libro nuevo = e.libro("Pruebas de software", "Paul Jorgensen", "Informática", 2013, 1);
        e.app.busqueda.indexar(nuevo);
        assertEquals(1, e.app.busqueda.buscar(CriteriosBusqueda.texto("jorgensen")).total());
        e.app.busqueda.quitar(nuevo.id());
        assertEquals(0, e.app.busqueda.buscar(CriteriosBusqueda.texto("jorgensen")).total());
    }

    @Test
    public void validaLimitesDeLosParametros() {
        String[] invalidos = {"pagina0", "tamano0", "tamano51", "anio1449", "anio2101", "rangoInvertido", "texto101"};
        for (String caso : invalidos) {
            try {
                switch (caso) {
                    case "pagina0" -> buscar(null, null, null, null, null, false, Orden.TITULO, 0, 20);
                    case "tamano0" -> buscar(null, null, null, null, null, false, Orden.TITULO, 1, 0);
                    case "tamano51" -> buscar(null, null, null, null, null, false, Orden.TITULO, 1, 51);
                    case "anio1449" -> buscar(null, null, null, 1449, null, false, Orden.TITULO, 1, 20);
                    case "anio2101" -> buscar(null, null, null, null, 2101, false, Orden.TITULO, 1, 20);
                    case "rangoInvertido" -> buscar(null, null, null, 2012, 2008, false, Orden.TITULO, 1, 20);
                    default -> buscar("x".repeat(101), null, null, null, null, false, Orden.TITULO, 1, 20);
                }
                throw new AssertionError("Se esperaba error de validación en " + caso);
            } catch (ErrorNegocio error) {
                assertEquals(caso, 400, error.estado());
            }
        }
    }

    @Test
    public void aceptaLosValoresLimiteValidos() {
        assertEquals(1, buscar(null, null, null, null, null, false, Orden.TITULO, 1, 1).libros().size());
        assertEquals(8, buscar(null, null, null, 1450, 2100, false, Orden.TITULO, 1, 50).total());
        assertEquals(0, buscar("x".repeat(100), null, null, null, null, false, Orden.TITULO, 1, 20).total());
    }

    @Test
    public void textoSoloConSimbolosDevuelveTodoElCatalogo() {
        assertTrue(e.app.busqueda.buscar(CriteriosBusqueda.texto("¿?' OR '1'='1")).total() <= 8);
        assertEquals(8, e.app.busqueda.buscar(CriteriosBusqueda.texto("¿?¡!")).total());
    }
}

package cl.libmanage.unitarias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cl.libmanage.dominio.ErrorNegocio;
import cl.libmanage.dominio.Libro;
import cl.libmanage.servicio.CriteriosBusqueda;
import cl.libmanage.servicio.Isbn;
import cl.libmanage.soporte.Escenario;
import org.junit.Before;
import org.junit.Test;

/** Gestión de inventario: clases de equivalencia y valores límite de cada campo (caja negra). */
public class InventarioServiceTest {
    private static final String ISBN_VALIDO = "978-0-13-468599-1";  // Effective Java, 3.ª ed.
    private Escenario e;

    @Before
    public void preparar() {
        e = new Escenario();
    }

    private Libro agregar(String isbn, String titulo, int anio, int ejemplares) {
        return e.app.inventario.agregar(isbn, titulo, "Joshua Bloch", "Informática", anio, ejemplares);
    }

    private void esperar400(Runnable accion) {
        try {
            accion.run();
            fail("Se esperaba un error 400");
        } catch (ErrorNegocio error) {
            assertEquals(400, error.estado());
        }
    }

    @Test
    public void isbnClasesDeEquivalencia() {
        assertTrue(Isbn.esValido(ISBN_VALIDO));
        assertTrue(Isbn.esValido("9791234567896"));            // prefijo 979
        assertFalse(Isbn.esValido("9780134685992"));           // dígito verificador incorrecto
        assertFalse(Isbn.esValido("978013468599"));            // 12 dígitos
        assertFalse(Isbn.esValido("97801346859911"));          // 14 dígitos
        assertFalse(Isbn.esValido("9770134685991"));           // prefijo distinto de 978/979
        assertFalse(Isbn.esValido("97801346859X1"));           // contiene letras
        assertFalse(Isbn.esValido(null));
    }

    @Test
    public void agregaLibroValidoYQuedaBuscable() {
        Libro libro = agregar(ISBN_VALIDO, "Effective Java", 2018, 4);
        assertEquals("9780134685991", libro.isbn());
        assertEquals(4, libro.disponibles());
        assertEquals(1, e.app.busqueda.buscar(CriteriosBusqueda.texto("effective")).total());
    }

    @Test
    public void ejemplaresValoresLimite() {
        esperar400(() -> agregar(ISBN_VALIDO, "Effective Java", 2018, 0));
        esperar400(() -> agregar(ISBN_VALIDO, "Effective Java", 2018, 101));
        assertEquals(1, agregar(ISBN_VALIDO, "Effective Java", 2018, 1).ejemplares());
        assertEquals(100, agregar("9791234567896", "Otro", 2018, 100).ejemplares());
    }

    @Test
    public void anioValoresLimite() {
        esperar400(() -> agregar(ISBN_VALIDO, "Incunable", 1449, 1));
        esperar400(() -> agregar(ISBN_VALIDO, "Del futuro", 2028, 1));  // año actual del reloj: 2026
        assertEquals(1450, agregar(ISBN_VALIDO, "Incunable", 1450, 1).anio());
        assertEquals(2027, agregar("9791234567896", "Preventa", 2027, 1).anio());
    }

    @Test
    public void tituloObligatorioYLargoMaximo() {
        esperar400(() -> agregar(ISBN_VALIDO, "   ", 2018, 1));
        esperar400(() -> agregar(ISBN_VALIDO, "x".repeat(201), 2018, 1));
        assertEquals(200, agregar(ISBN_VALIDO, "x".repeat(200), 2018, 1).titulo().length());
    }

    @Test
    public void rechazaIsbnInvalidoYDuplicado() {
        esperar400(() -> agregar("9780134685992", "Effective Java", 2018, 1));
        agregar(ISBN_VALIDO, "Effective Java", 2018, 1);
        try {
            agregar("9780134685991", "Copia", 2018, 1);
            fail("Se esperaba conflicto por ISBN duplicado");
        } catch (ErrorNegocio error) {
            assertEquals(409, error.estado());
        }
    }

    @Test
    public void noPermiteDejarMenosEjemplaresQueLosPrestados() {
        e.app.prestamosService.prestar(e.estudiante.id(), 1);
        e.app.prestamosService.prestar(e.docente.id(), 1);
        try {
            e.app.inventario.ajustarEjemplares(1, 1);
            fail("Se esperaba conflicto");
        } catch (ErrorNegocio error) {
            assertEquals(409, error.estado());
        }
        Libro ajustado = e.app.inventario.ajustarEjemplares(1, 2);
        assertEquals(0, ajustado.disponibles());
        assertEquals(5, e.app.inventario.ajustarEjemplares(1, 5).ejemplares());
        assertEquals(3, e.libros.porId(1).orElseThrow().disponibles());
    }

    @Test
    public void bajaSoloSinPrestamosActivos() {
        e.app.prestamosService.prestar(e.estudiante.id(), 8);
        try {
            e.app.inventario.darDeBaja(8);
            fail("Se esperaba conflicto");
        } catch (ErrorNegocio error) {
            assertEquals(409, error.estado());
        }
        e.app.inventario.darDeBaja(7);
        assertTrue(e.libros.porId(7).isEmpty());
        assertEquals(0, e.app.busqueda.buscar(CriteriosBusqueda.texto("soledad")).total());
    }

    @Test
    public void ajustarODarDeBajaLibroInexistente() {
        for (Runnable accion : new Runnable[] {() -> e.app.inventario.ajustarEjemplares(999, 2),
            () -> e.app.inventario.darDeBaja(999)}) {
            try {
                accion.run();
                fail("Se esperaba 404");
            } catch (ErrorNegocio error) {
                assertEquals(404, error.estado());
            }
        }
    }
}

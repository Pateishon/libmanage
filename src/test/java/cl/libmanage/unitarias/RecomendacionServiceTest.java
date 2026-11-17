package cl.libmanage.unitarias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cl.libmanage.dominio.ErrorNegocio;
import cl.libmanage.dominio.Libro;
import cl.libmanage.dominio.Prestamo;
import cl.libmanage.dominio.Rol;
import cl.libmanage.dominio.Usuario;
import cl.libmanage.servicio.RecomendacionService;
import cl.libmanage.soporte.Escenario;
import java.time.LocalDate;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/** Sistema de recomendación basado en el historial de préstamos. */
public class RecomendacionServiceTest {
    private Escenario e;
    private long siguienteUsuario = 100;

    @Before
    public void preparar() {
        e = new Escenario();
    }

    /** Registra un historial ya devuelto para un lector ficticio. */
    private long lectorQueLeyo(long... libros) {
        long usuario = siguienteUsuario++;
        for (long libro : libros) {
            LocalDate inicio = LocalDate.of(2026, 9, 1);
            Prestamo p = e.prestamos.guardar(new Prestamo(0, usuario, libro, inicio, inicio.plusDays(7), inicio.plusDays(3)));
            e.app.recomendaciones.registrarPrestamo(p);
        }
        return usuario;
    }

    private static List<Long> ids(List<Libro> libros) {
        return libros.stream().map(Libro::id).toList();
    }

    @Test
    public void recomiendaLoQueLeyeronQuienesCompartenLecturas() {
        lectorQueLeyo(1, 4);
        lectorQueLeyo(1, 4);
        lectorQueLeyo(1, 3);
        long ana = lectorQueLeyo(1);
        List<Long> recomendados = ids(e.app.recomendaciones.recomendar(ana, 2));
        assertEquals(List.of(4L, 3L), recomendados);  // 4 co-ocurre 2 veces con el libro 1; 3, una vez
    }

    @Test
    public void nuncaRecomiendaLibrosYaLeidos() {
        lectorQueLeyo(1, 2, 3);
        long ana = lectorQueLeyo(1, 2);
        List<Long> recomendados = ids(e.app.recomendaciones.recomendar(ana, 10));
        assertFalse(recomendados.contains(1L));
        assertFalse(recomendados.contains(2L));
        assertEquals(3L, (long) recomendados.get(0));
    }

    @Test
    public void excluyeLibrosSinEjemplaresDisponibles() {
        lectorQueLeyo(1, 3);
        long ana = lectorQueLeyo(1);
        e.libros.ajustarDisponibles(3, -1);  // "Refactorización" tiene un solo ejemplar
        assertFalse(ids(e.app.recomendaciones.recomendar(ana, 10)).contains(3L));
    }

    @Test
    public void usuarioSinHistorialRecibeLosMasPopulares() {
        lectorQueLeyo(7);
        lectorQueLeyo(7, 5);
        List<Long> recomendados = ids(e.app.recomendaciones.recomendar(999, 2));
        assertEquals(List.of(7L, 5L), recomendados);
    }

    @Test
    public void completaConLaCategoriaFavoritaCuandoNoHayCoincidencias() {
        long ana = lectorQueLeyo(5);  // solo leyó Matemática y nadie más lo leyó
        assertEquals(6L, (long) e.app.recomendaciones.recomendar(ana, 1).get(0).id());
    }

    @Test
    public void unPrestamoNuevoActualizaLasRecomendaciones() {
        lectorQueLeyo(1, 4);
        long ana = lectorQueLeyo(1);
        assertEquals(4L, (long) e.app.recomendaciones.recomendar(ana, 1).get(0).id());
        Usuario real = e.estudiante;
        e.app.prestamosService.prestar(real.id(), 4);
        assertFalse(ids(e.app.recomendaciones.recomendar(real.id(), 10)).contains(4L));
    }

    @Test
    public void respetaLaCantidadPedidaYSusLimites() {
        assertEquals(5, e.app.recomendaciones.recomendar(1, 5).size());
        assertEquals(8, e.app.recomendaciones.recomendar(1, RecomendacionService.MAX_RECOMENDACIONES).size());
        for (int invalido : new int[] {0, RecomendacionService.MAX_RECOMENDACIONES + 1}) {
            try {
                e.app.recomendaciones.recomendar(1, invalido);
                throw new AssertionError("Se esperaba error para " + invalido);
            } catch (ErrorNegocio error) {
                assertEquals(400, error.estado());
            }
        }
    }

    @Test
    public void elHistorialExistenteSeCargaAlIniciar() {
        lectorQueLeyo(2, 6);
        RecomendacionService nuevo = new RecomendacionService(e.libros, e.prestamos);
        long ana = siguienteUsuario++;
        e.prestamos.guardar(new Prestamo(0, ana, 2, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8), null));
        RecomendacionService recargado = new RecomendacionService(e.libros, e.prestamos);
        assertTrue(nuevo.recomendar(ana, 8).size() > 0);
        assertEquals(6L, (long) recargado.recomendar(ana, 1).get(0).id());
        assertEquals(Rol.ESTUDIANTE, e.estudiante.rol());
    }
}

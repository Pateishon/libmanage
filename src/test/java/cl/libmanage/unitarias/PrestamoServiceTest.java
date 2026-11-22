package cl.libmanage.unitarias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import cl.libmanage.dominio.ErrorNegocio;
import cl.libmanage.dominio.Prestamo;
import cl.libmanage.soporte.Escenario;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;

/**
 * Caja blanca de PrestamoService. prestar() tiene 7 decisiones → complejidad ciclomática V(G) = 8,
 * por lo que hay 8 caminos independientes (C1 a C8); devolver() tiene V(G) = 5 (D1 a D5).
 * Cada prueba recorre un camino distinto.
 */
public class PrestamoServiceTest {
    private Escenario e;

    @Before
    public void preparar() {
        e = new Escenario();
    }

    private void esperarError(int estado, String fragmento, Runnable accion) {
        try {
            accion.run();
            fail("Se esperaba error " + estado);
        } catch (ErrorNegocio error) {
            assertEquals(estado, error.estado());
            if (!error.getMessage().contains(fragmento)) {
                fail("Mensaje inesperado: " + error.getMessage());
            }
        }
    }

    // ---------- prestar(): caminos C1 a C8 ----------
    @Test
    public void c1UsuarioInexistente() {
        esperarError(404, "Usuario", () -> e.app.prestamosService.prestar(999, 1));
    }

    @Test
    public void c2UsuarioBloqueado() {
        esperarError(403, "bloqueado", () -> e.app.prestamosService.prestar(e.bloqueado.id(), 1));
    }

    @Test
    public void c3TienePrestamoVencido() {
        e.app.prestamosService.prestar(e.estudiante.id(), 1);
        e.reloj.avanzar(Duration.ofDays(8));  // plazo de estudiante: 7 días
        esperarError(409, "vencidos", () -> e.app.prestamosService.prestar(e.estudiante.id(), 2));
    }

    @Test
    public void c4AlcanzaElLimiteDeSuRol() {
        for (long libro = 1; libro <= 3; libro++) {
            e.app.prestamosService.prestar(e.estudiante.id(), libro);
        }
        esperarError(409, "límite de 3", () -> e.app.prestamosService.prestar(e.estudiante.id(), 4));
        for (long libro = 4; libro <= 8; libro++) {
            e.app.prestamosService.prestar(e.docente.id(), libro);  // el docente admite 5
        }
        esperarError(409, "límite de 5", () -> e.app.prestamosService.prestar(e.docente.id(), 1));
    }

    @Test
    public void c5YaTieneElMismoLibro() {
        e.app.prestamosService.prestar(e.estudiante.id(), 1);
        esperarError(409, "Ya tiene", () -> e.app.prestamosService.prestar(e.estudiante.id(), 1));
    }

    @Test
    public void c6LibroInexistente() {
        esperarError(404, "Libro", () -> e.app.prestamosService.prestar(e.estudiante.id(), 999));
    }

    @Test
    public void c7SinEjemplaresDisponibles() {
        e.app.prestamosService.prestar(e.docente.id(), 3);  // único ejemplar
        esperarError(409, "No hay ejemplares", () -> e.app.prestamosService.prestar(e.estudiante.id(), 3));
    }

    @Test
    public void c8PrestamoExitosoConPlazoSegunRol() {
        Prestamo deEstudiante = e.app.prestamosService.prestar(e.estudiante.id(), 1);
        Prestamo deDocente = e.app.prestamosService.prestar(e.docente.id(), 1);
        assertEquals(LocalDate.of(2026, 11, 9), deEstudiante.limite());
        assertEquals(LocalDate.of(2026, 11, 16), deDocente.limite());
        assertEquals(1, e.libros.porId(1).orElseThrow().disponibles());
    }

    @Test
    public void elDiaLimiteAunNoCuentaComoVencido() {
        e.app.prestamosService.prestar(e.estudiante.id(), 1);
        e.reloj.avanzar(Duration.ofDays(7));  // valor límite: hoy == fecha límite
        assertNotNull(e.app.prestamosService.prestar(e.estudiante.id(), 2));
    }

    // ---------- devolver(): caminos D1 a D5 ----------
    @Test
    public void d1PrestamoInexistente() {
        esperarError(404, "Préstamo", () -> e.app.prestamosService.devolver(999, e.estudiante.id(), false));
    }

    @Test
    public void d2NoPuedeDevolverPrestamoAjeno() {
        Prestamo p = e.app.prestamosService.prestar(e.docente.id(), 1);
        esperarError(404, "Préstamo", () -> e.app.prestamosService.devolver(p.id(), e.estudiante.id(), false));
    }

    @Test
    public void d3NoSePuedeDevolverDosVeces() {
        Prestamo p = e.app.prestamosService.prestar(e.estudiante.id(), 3);
        e.app.prestamosService.devolver(p.id(), e.estudiante.id(), false);
        esperarError(409, "ya fue devuelto", () -> e.app.prestamosService.devolver(p.id(), e.estudiante.id(), false));
        assertEquals(1, e.libros.porId(3).orElseThrow().disponibles());  // no queda en 2 ejemplares de 1
    }

    @Test
    public void d4DevolucionExitosaRestituyeElEjemplar() {
        Prestamo p = e.app.prestamosService.prestar(e.estudiante.id(), 3);
        e.reloj.avanzar(Duration.ofDays(2));
        Prestamo devuelto = e.app.prestamosService.devolver(p.id(), e.estudiante.id(), false);
        assertEquals(LocalDate.of(2026, 11, 4), devuelto.devolucion());
        assertEquals(1, e.libros.porId(3).orElseThrow().disponibles());
    }

    @Test
    public void d5AdministradorPuedeRegistrarDevolucionAjena() {
        Prestamo p = e.app.prestamosService.prestar(e.estudiante.id(), 3);
        assertNotNull(e.app.prestamosService.devolver(p.id(), e.admin.id(), true).devolucion());
    }

    // ---------- concurrencia ----------
    @Test
    public void ochentaSolicitudesSimultaneasNoPrestanMasEjemplaresDeLosQueHay() throws Exception {
        List<Long> usuarios = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            usuarios.add(e.usuarios.guardar(new cl.libmanage.dominio.Usuario(0, "U" + i, "u" + i + "@u.cl",
                    "1-9", "x", cl.libmanage.dominio.Rol.ESTUDIANTE, true)).id());
        }
        AtomicInteger exitosos = new AtomicInteger();
        CountDownLatch partida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(16);
        for (Long usuario : usuarios) {
            hilos.submit(() -> {
                partida.await();
                try {
                    e.app.prestamosService.prestar(usuario, 4);  // 2 ejemplares
                    exitosos.incrementAndGet();
                } catch (ErrorNegocio esperado) {
                    // sin ejemplares
                }
                return null;
            });
        }
        partida.countDown();
        hilos.shutdown();
        hilos.awaitTermination(10, TimeUnit.SECONDS);
        assertEquals(2, exitosos.get());
        assertEquals(0, e.libros.porId(4).orElseThrow().disponibles());
    }

    @Test
    public void solicitudesSimultaneasDelMismoUsuarioRespetanSuLimite() throws Exception {
        AtomicInteger exitosos = new AtomicInteger();
        CountDownLatch partida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        for (long libro = 1; libro <= 8; libro++) {
            long id = libro;
            hilos.submit(() -> {
                partida.await();
                try {
                    e.app.prestamosService.prestar(e.estudiante.id(), id);
                    exitosos.incrementAndGet();
                } catch (ErrorNegocio esperado) {
                    // límite alcanzado
                }
                return null;
            });
        }
        partida.countDown();
        hilos.shutdown();
        hilos.awaitTermination(10, TimeUnit.SECONDS);
        assertEquals(3, exitosos.get());
    }
}

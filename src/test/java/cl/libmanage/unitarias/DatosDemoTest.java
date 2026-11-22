package cl.libmanage.unitarias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cl.libmanage.dominio.Libro;
import cl.libmanage.repositorio.LibroRepositorioMemoria;
import cl.libmanage.repositorio.PrestamoRepositorioMemoria;
import cl.libmanage.repositorio.UsuarioRepositorioMemoria;
import cl.libmanage.servicio.Isbn;
import cl.libmanage.soporte.Escenario;
import cl.libmanage.web.Aplicacion;
import cl.libmanage.web.DatosDemo;
import java.util.List;
import org.junit.Test;

/** Los datos de demostración (usados en las pruebas de carga y de aceptación) son válidos y consistentes. */
public class DatosDemoTest {
    @Test
    public void generaUnCatalogoValidoConUsuariosEHistorial() {
        Escenario base = new Escenario();
        LibroRepositorioMemoria libros = new LibroRepositorioMemoria();
        UsuarioRepositorioMemoria usuarios = new UsuarioRepositorioMemoria(base.cifrador);
        PrestamoRepositorioMemoria prestamos = new PrestamoRepositorioMemoria();
        DatosDemo.poblar(libros, usuarios, prestamos, base.contrasenas, base.reloj, 300, 40);
        List<Libro> catalogo = libros.todos();
        assertEquals(300, catalogo.size());
        assertTrue(catalogo.stream().allMatch(l -> Isbn.esValido(l.isbn())));
        assertEquals(300, catalogo.stream().map(Libro::isbn).distinct().count());
        assertTrue(prestamos.todos().stream().noneMatch(p -> p.activo()));

        Aplicacion app = new Aplicacion(libros, usuarios, prestamos, base.contrasenas, Escenario.CLAVE_TOKENS, base.reloj);
        var ana = app.autenticacion.ingresar(DatosDemo.EMAIL_ESTUDIANTE, DatosDemo.CLAVE_ESTUDIANTE).usuario();
        app.autenticacion.ingresar("lector7@uautonoma.cl", DatosDemo.CLAVE_LECTORES);
        List<Libro> recomendados = app.recomendaciones.recomendar(ana.id(), 5);
        assertEquals(5, recomendados.size());
        assertFalse(recomendados.stream().anyMatch(l -> l.titulo().equals("Código limpio")));  // ya lo leyó
        assertEquals("Informática", recomendados.get(0).categoria());
    }
}

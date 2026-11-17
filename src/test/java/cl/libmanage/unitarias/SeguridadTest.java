package cl.libmanage.unitarias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cl.libmanage.dominio.Rol;
import cl.libmanage.repositorio.UsuarioRepositorioMemoria.RegistroAlmacenado;
import cl.libmanage.seguridad.CifradorDatos;
import cl.libmanage.seguridad.Contrasenas;
import cl.libmanage.seguridad.Enmascarador;
import cl.libmanage.seguridad.LimitadorIntentos;
import cl.libmanage.seguridad.TokenService;
import cl.libmanage.soporte.Escenario;
import java.time.Duration;
import java.util.Base64;
import org.junit.Test;

/** Componentes de seguridad que protegen los datos personales y el acceso. */
public class SeguridadTest {
    private final Escenario e = new Escenario();

    @Test
    public void contrasenasSeGuardanConSalYNoEnTextoClaro() {
        Contrasenas c = new Contrasenas(1_000);
        String h1 = c.hash("MiClave123");
        String h2 = c.hash("MiClave123");
        assertNotEquals("la sal debe ser distinta en cada hash", h1, h2);
        assertFalse(h1.contains("MiClave123"));
        assertTrue(h1.startsWith("pbkdf2$1000$"));
        assertTrue(c.verificar("MiClave123", h1));
        assertFalse(c.verificar("miclave123", h1));
        assertFalse(c.verificar("MiClave123", "formato-invalido"));
        assertFalse(c.verificar("MiClave123", null));
    }

    @Test
    public void produccionUsaAlMenos600MilIteraciones() {
        assertTrue(Contrasenas.ITERACIONES_PRODUCCION >= 600_000);
    }

    @Test
    public void tokenValidoAlteradoYExpirado() {
        TokenService tokens = e.app.tokens;
        String token = tokens.emitir(42, Rol.DOCENTE);
        assertEquals(42, tokens.validar(token).orElseThrow().usuarioId());

        // Cambiar el rol dentro del token invalida la firma.
        String carga = new String(Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.'))));
        String falsificada = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(carga.replace("DOCENTE", "ADMIN").getBytes()) + token.substring(token.indexOf('.'));
        assertTrue(tokens.validar(falsificada).isEmpty());

        // Un token firmado con otra clave no es válido.
        TokenService otra = new TokenService(new byte[32], e.reloj, Duration.ofMinutes(30));
        assertTrue(tokens.validar(otra.emitir(42, Rol.ADMIN)).isEmpty());

        e.reloj.avanzar(Duration.ofMinutes(31));
        assertTrue("debe expirar a los 30 minutos", tokens.validar(token).isEmpty());
        for (String basura : new String[] {null, "", "abc", "a.b", "x".repeat(600)}) {
            assertTrue(tokens.validar(basura).isEmpty());
        }
    }

    @Test
    public void bloqueaTrasCincoIntentosFallidosYLiberaAlTerminarLaVentana() {
        LimitadorIntentos limitador = new LimitadorIntentos(5, Duration.ofMinutes(15), e.reloj);
        for (int i = 0; i < 4; i++) {
            limitador.registrarFallo("ana@u.cl");
        }
        assertFalse(limitador.bloqueado("ana@u.cl"));
        limitador.registrarFallo("ana@u.cl");
        assertTrue(limitador.bloqueado("ana@u.cl"));
        assertFalse("otras cuentas no se ven afectadas", limitador.bloqueado("luis@u.cl"));
        e.reloj.avanzar(Duration.ofMinutes(16));
        assertFalse(limitador.bloqueado("ana@u.cl"));
    }

    @Test
    public void cifradoAesGcmDeDatosPersonales() {
        CifradorDatos cifrador = e.cifrador;
        String c1 = cifrador.cifrar("12.345.678-5");
        String c2 = cifrador.cifrar("12.345.678-5");
        assertNotEquals("IV aleatorio: el mismo dato cifra distinto", c1, c2);
        assertEquals("12.345.678-5", cifrador.descifrar(c1));

        byte[] alterado = Base64.getDecoder().decode(c1);
        alterado[alterado.length - 1] ^= 1;
        try {
            cifrador.descifrar(Base64.getEncoder().encodeToString(alterado));
            fail("GCM debe detectar la alteración");
        } catch (IllegalStateException esperado) {
            // integridad verificada
        }
        try {
            new CifradorDatos(new byte[32]).descifrar(c1);
            fail("Otra clave no debe poder descifrar");
        } catch (IllegalStateException esperado) {
            // confidencialidad verificada
        }
        assertEquals(cifrador.indiceCiego("Ana@U.cl "), cifrador.indiceCiego("ana@u.cl"));
    }

    @Test
    public void elAlmacenamientoNoContieneDatosPersonalesEnClaro() {
        RegistroAlmacenado fila = e.usuarios.registroAlmacenado(e.estudiante.id()).orElseThrow();
        String filaCompleta = fila.toString();
        assertFalse(filaCompleta.contains("ana@u.cl"));
        assertFalse(filaCompleta.contains("12.345.678-5"));
        assertEquals("ana@u.cl", e.usuarios.porEmail("ANA@u.cl").orElseThrow().email());
    }

    @Test
    public void enmascaraRutYEmail() {
        assertEquals("**.***.678-5", Enmascarador.rut("12.345.678-5"));
        assertEquals("*.***.543-3", Enmascarador.rut("9.876.543-3"));
        assertEquals("a***@uautonoma.cl", Enmascarador.email("ana.torres@uautonoma.cl"));
        assertEquals("***", Enmascarador.email("sin-arroba"));
        assertEquals("***", Enmascarador.rut(null));
        assertEquals("***", Enmascarador.rut("1-9"));
        assertEquals("***", Enmascarador.email(null));
    }

    @Test
    public void claveDeCifradoDebeSerDe256Bits() {
        try {
            new CifradorDatos(new byte[16]);
            fail("Se esperaba rechazo de la clave corta");
        } catch (IllegalArgumentException esperado) {
            // AES-256 obligatorio
        }
    }
}

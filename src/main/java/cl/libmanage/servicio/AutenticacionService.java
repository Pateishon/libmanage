package cl.libmanage.servicio;

import cl.libmanage.dominio.ErrorNegocio;
import cl.libmanage.dominio.Usuario;
import cl.libmanage.repositorio.UsuarioRepositorio;
import cl.libmanage.seguridad.Contrasenas;
import cl.libmanage.seguridad.LimitadorIntentos;
import cl.libmanage.seguridad.TokenService;
import java.util.Optional;

/** Inicio de sesión con bloqueo por intentos fallidos y mensaje genérico (no revela si el email existe). */
public final class AutenticacionService {
    public record Ingreso(String token, Usuario usuario) {
    }

    private final UsuarioRepositorio usuarios;
    private final Contrasenas contrasenas;
    private final TokenService tokens;
    private final LimitadorIntentos limitador;

    public AutenticacionService(UsuarioRepositorio usuarios, Contrasenas contrasenas, TokenService tokens,
                                LimitadorIntentos limitador) {
        this.usuarios = usuarios;
        this.contrasenas = contrasenas;
        this.tokens = tokens;
        this.limitador = limitador;
    }

    public Ingreso ingresar(String email, String contrasena) {
        if (email == null || contrasena == null || email.length() > 254 || contrasena.length() > 128) {
            throw new ErrorNegocio(400, "Email y contraseña son obligatorios");
        }
        String clave = email.trim().toLowerCase();
        if (limitador.bloqueado(clave)) {
            throw new ErrorNegocio(429, "Demasiados intentos fallidos. Intente nuevamente en 15 minutos");
        }
        Optional<Usuario> usuario = usuarios.porEmail(clave);
        if (usuario.isEmpty() || !usuario.get().activo()
                || !contrasenas.verificar(contrasena, usuario.get().hashContrasena())) {
            limitador.registrarFallo(clave);
            throw new ErrorNegocio(401, "Credenciales inválidas");
        }
        limitador.limpiar(clave);
        return new Ingreso(tokens.emitir(usuario.get().id(), usuario.get().rol()), usuario.get());
    }
}

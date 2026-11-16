package cl.libmanage.repositorio;

import cl.libmanage.dominio.Rol;
import cl.libmanage.dominio.Usuario;
import cl.libmanage.seguridad.CifradorDatos;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Guarda los usuarios con email y RUT cifrados (AES-GCM). El email se busca por un índice ciego,
 * de modo que el almacenamiento nunca contiene datos personales en texto claro.
 */
public final class UsuarioRepositorioMemoria implements UsuarioRepositorio {
    /** Fila tal como quedaría en la base de datos. */
    public record RegistroAlmacenado(long id, String nombre, String emailCifrado, String rutCifrado,
                                     String indiceEmail, String hashContrasena, Rol rol, boolean activo) {
    }

    private final CifradorDatos cifrador;
    private final Map<Long, RegistroAlmacenado> filas = new ConcurrentHashMap<>();
    private final Map<String, Long> porIndiceEmail = new ConcurrentHashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    public UsuarioRepositorioMemoria(CifradorDatos cifrador) {
        this.cifrador = cifrador;
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        Usuario conId = usuario.id() == 0 ? usuario.conId(secuencia.incrementAndGet()) : usuario;
        String indice = cifrador.indiceCiego(conId.email());
        filas.put(conId.id(), new RegistroAlmacenado(conId.id(), conId.nombre(), conId.email(),
                conId.rut(), indice, conId.hashContrasena(), conId.rol(), conId.activo()));
        porIndiceEmail.put(indice, conId.id());
        return conId;
    }

    @Override
    public Optional<Usuario> porId(long id) {
        return Optional.ofNullable(filas.get(id)).map(this::aUsuario);
    }

    @Override
    public Optional<Usuario> porEmail(String email) {
        Long id = porIndiceEmail.get(cifrador.indiceCiego(email));
        return id == null ? Optional.empty() : porId(id);
    }

    /** Devuelve la fila almacenada sin descifrar (usado por las pruebas de seguridad). */
    public Optional<RegistroAlmacenado> registroAlmacenado(long id) {
        return Optional.ofNullable(filas.get(id));
    }

    private Usuario aUsuario(RegistroAlmacenado fila) {
        return new Usuario(fila.id(), fila.nombre(), fila.emailCifrado(), fila.rutCifrado(), fila.hashContrasena(),
                fila.rol(), fila.activo());
    }
}

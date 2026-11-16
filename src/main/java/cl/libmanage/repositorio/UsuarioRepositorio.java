package cl.libmanage.repositorio;

import cl.libmanage.dominio.Usuario;
import java.util.Optional;

public interface UsuarioRepositorio {
    Usuario guardar(Usuario usuario);

    Optional<Usuario> porId(long id);

    Optional<Usuario> porEmail(String email);
}

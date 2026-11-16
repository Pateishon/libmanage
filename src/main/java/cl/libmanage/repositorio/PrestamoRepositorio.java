package cl.libmanage.repositorio;

import cl.libmanage.dominio.Prestamo;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PrestamoRepositorio {
    Prestamo guardar(Prestamo prestamo);

    Optional<Prestamo> porId(long id);

    List<Prestamo> porUsuario(long usuarioId);

    List<Prestamo> todos();

    /** Marca el préstamo como devuelto solo si seguía activo (operación atómica). */
    boolean marcarDevuelto(long id, LocalDate fecha);
}

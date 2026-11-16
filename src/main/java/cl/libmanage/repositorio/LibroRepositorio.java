package cl.libmanage.repositorio;

import cl.libmanage.dominio.Libro;
import java.util.List;
import java.util.Optional;

/** Acceso a los libros. La implementación de producción usa MySQL ({@link JdbcLibroRepositorio}). */
public interface LibroRepositorio {
    Libro guardar(Libro libro);

    Optional<Libro> porId(long id);

    Optional<Libro> porIsbn(String isbn);

    List<Libro> todos();

    /**
     * Suma {@code delta} a los ejemplares disponibles de forma atómica. Devuelve false si el
     * libro no existe o si el resultado quedaría fuera de [0, ejemplares].
     */
    boolean ajustarDisponibles(long id, int delta);

    void eliminar(long id);
}

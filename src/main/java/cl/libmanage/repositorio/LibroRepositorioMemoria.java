package cl.libmanage.repositorio;

import cl.libmanage.dominio.Libro;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Implementación en memoria, segura para hilos; se usa en pruebas y en modo demostración. */
public final class LibroRepositorioMemoria implements LibroRepositorio {
    private final Map<Long, Libro> libros = new ConcurrentHashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    @Override
    public Libro guardar(Libro libro) {
        Libro conId = libro.id() == 0 ? libro.conId(secuencia.incrementAndGet()) : libro;
        secuencia.accumulateAndGet(conId.id(), Math::max);
        libros.put(conId.id(), conId);
        return conId;
    }

    @Override
    public Optional<Libro> porId(long id) {
        return Optional.ofNullable(libros.get(id));
    }

    @Override
    public Optional<Libro> porIsbn(String isbn) {
        return libros.values().stream().filter(l -> l.isbn().equals(isbn)).findFirst();
    }

    @Override
    public List<Libro> todos() {
        return new ArrayList<>(libros.values());
    }

    @Override
    public boolean ajustarDisponibles(long id, int delta) {
        AtomicBoolean ajustado = new AtomicBoolean(false);
        libros.computeIfPresent(id, (clave, libro) -> {
            int nuevos = libro.disponibles() + delta;
            if (nuevos < 0 || nuevos > libro.ejemplares()) {
                return libro;
            }
            ajustado.set(true);
            return libro.conDisponibles(nuevos);
        });
        return ajustado.get();
    }

    @Override
    public void eliminar(long id) {
        libros.remove(id);
    }
}

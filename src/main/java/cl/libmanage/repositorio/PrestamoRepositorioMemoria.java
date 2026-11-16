package cl.libmanage.repositorio;

import cl.libmanage.dominio.Prestamo;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class PrestamoRepositorioMemoria implements PrestamoRepositorio {
    private final Map<Long, Prestamo> prestamos = new ConcurrentHashMap<>();
    private final Map<Long, List<Long>> porUsuario = new ConcurrentHashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    @Override
    public Prestamo guardar(Prestamo prestamo) {
        Prestamo conId = prestamo.id() == 0 ? prestamo.conId(secuencia.incrementAndGet()) : prestamo;
        if (prestamos.put(conId.id(), conId) == null) {
            porUsuario.computeIfAbsent(conId.usuarioId(), k -> new CopyOnWriteArrayList<>()).add(conId.id());
        }
        return conId;
    }

    @Override
    public Optional<Prestamo> porId(long id) {
        return Optional.ofNullable(prestamos.get(id));
    }

    @Override
    public List<Prestamo> porUsuario(long usuarioId) {
        List<Prestamo> resultado = new ArrayList<>();
        for (Long id : porUsuario.getOrDefault(usuarioId, List.of())) {
            resultado.add(prestamos.get(id));
        }
        resultado.sort(Comparator.comparing(Prestamo::inicio).thenComparing(Prestamo::id));
        return resultado;
    }

    @Override
    public List<Prestamo> todos() {
        return new ArrayList<>(prestamos.values());
    }

    @Override
    public boolean marcarDevuelto(long id, LocalDate fecha) {
        AtomicBoolean marcado = new AtomicBoolean(false);
        prestamos.computeIfPresent(id, (clave, prestamo) -> {
            if (!prestamo.activo()) {
                return prestamo;
            }
            marcado.set(true);
            return prestamo.devuelto(fecha);
        });
        return marcado.get();
    }
}

package cl.libmanage.servicio;

import cl.libmanage.dominio.ErrorNegocio;
import cl.libmanage.dominio.Libro;
import cl.libmanage.dominio.Prestamo;
import cl.libmanage.repositorio.LibroRepositorio;
import cl.libmanage.repositorio.PrestamoRepositorio;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Recomendaciones basadas en el historial de préstamos ("quienes leyeron X también leyeron Y").
 * La matriz de co-ocurrencia se actualiza de forma incremental con cada préstamo y los resultados
 * se guardan en caché hasta el siguiente préstamo, en vez de recalcularse en cada consulta.
 * Orden de relleno: co-ocurrencia → categorías favoritas del usuario → libros más populares.
 * Nunca recomienda libros que el usuario ya leyó ni libros sin ejemplares disponibles.
 */
public final class RecomendacionService {
    public static final int MAX_RECOMENDACIONES = 20;

    private record EnCache(long version, List<Long> ids) {
    }

    private final LibroRepositorio libros;
    private final Map<Long, Set<Long>> historial = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, Integer>> coocurrencia = new ConcurrentHashMap<>();
    private final Map<Long, Integer> popularidad = new ConcurrentHashMap<>();
    private final Map<Long, EnCache> cache = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();

    public RecomendacionService(LibroRepositorio libros, PrestamoRepositorio prestamos) {
        this.libros = libros;
        prestamos.todos().stream()
                .sorted(Comparator.comparing(Prestamo::inicio).thenComparingLong(Prestamo::id))
                .forEach(this::registrarPrestamo);
    }

    public synchronized void registrarPrestamo(Prestamo prestamo) {
        Set<Long> leidos = historial.computeIfAbsent(prestamo.usuarioId(), k -> ConcurrentHashMap.newKeySet());
        if (leidos.add(prestamo.libroId())) {
            for (Long otro : leidos) {
                if (otro != prestamo.libroId()) {
                    incrementar(otro, prestamo.libroId());
                    incrementar(prestamo.libroId(), otro);
                }
            }
            popularidad.merge(prestamo.libroId(), 1, Integer::sum);
        }
        version.incrementAndGet();
    }

    public List<Libro> recomendar(long usuarioId, int cantidad) {
        if (cantidad < 1 || cantidad > MAX_RECOMENDACIONES) {
            throw new ErrorNegocio(400, "Se pueden pedir entre 1 y " + MAX_RECOMENDACIONES + " recomendaciones");
        }
        long versionActual = version.get();
        EnCache guardado = cache.get(usuarioId);
        List<Long> ids;
        if (guardado != null && guardado.version() == versionActual) {
            ids = guardado.ids();
        } else {
            ids = calcular(usuarioId);
            cache.put(usuarioId, new EnCache(versionActual, ids));
        }
        List<Libro> resultado = new ArrayList<>();
        for (Long id : ids) {
            Optional<Libro> libro = libros.porId(id);
            if (libro.isPresent() && libro.get().disponibles() > 0) {
                resultado.add(libro.get());
                if (resultado.size() == cantidad) {
                    break;
                }
            }
        }
        return resultado;
    }

    private List<Long> calcular(long usuarioId) {
        Set<Long> leidos = historial.getOrDefault(usuarioId, Set.of());
        Map<Long, Integer> puntaje = new HashMap<>();
        for (Long leido : leidos) {
            coocurrencia.getOrDefault(leido, Map.of()).forEach((candidato, veces) -> {
                if (!leidos.contains(candidato)) {
                    puntaje.merge(candidato, veces, Integer::sum);
                }
            });
        }
        LinkedHashSet<Long> orden = new LinkedHashSet<>();
        puntaje.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed()
                        .thenComparing(e -> -popularidad.getOrDefault(e.getKey(), 0))
                        .thenComparing(Map.Entry::getKey))
                .limit(MAX_RECOMENDACIONES * 3L)
                .forEach(e -> orden.add(e.getKey()));

        Set<String> categoriasFavoritas = categoriasFavoritas(leidos);
        List<Libro> catalogo = libros.todos();
        catalogo.sort(Comparator.<Libro>comparingInt(l -> -popularidad.getOrDefault(l.id(), 0))
                .thenComparingLong(Libro::id));
        for (Libro libro : catalogo) {
            if (orden.size() >= MAX_RECOMENDACIONES * 3) {
                break;
            }
            if (!leidos.contains(libro.id()) && categoriasFavoritas.contains(libro.categoria())) {
                orden.add(libro.id());
            }
        }
        for (Libro libro : catalogo) {
            if (orden.size() >= MAX_RECOMENDACIONES * 3) {
                break;
            }
            if (!leidos.contains(libro.id())) {
                orden.add(libro.id());
            }
        }
        return List.copyOf(orden);
    }

    private Set<String> categoriasFavoritas(Set<Long> leidos) {
        Map<String, Integer> conteo = new HashMap<>();
        for (Long id : leidos) {
            libros.porId(id).ifPresent(l -> conteo.merge(l.categoria(), 1, Integer::sum));
        }
        Set<String> favoritas = new LinkedHashSet<>();
        conteo.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(2).forEach(e -> favoritas.add(e.getKey()));
        return favoritas;
    }

    private void incrementar(long desde, long hacia) {
        coocurrencia.computeIfAbsent(desde, k -> new ConcurrentHashMap<>()).merge(hacia, 1, Integer::sum);
    }
}

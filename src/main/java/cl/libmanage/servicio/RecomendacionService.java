package cl.libmanage.servicio;

import cl.libmanage.dominio.ErrorNegocio;
import cl.libmanage.dominio.Libro;
import cl.libmanage.dominio.Prestamo;
import cl.libmanage.repositorio.LibroRepositorio;
import cl.libmanage.repositorio.PrestamoRepositorio;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Recomendaciones según el historial de préstamos ("quienes leyeron X también leyeron Y"). */
public final class RecomendacionService {
    public static final int MAX_RECOMENDACIONES = 20;
    private final LibroRepositorio libros;
    private final PrestamoRepositorio prestamos;

    public RecomendacionService(LibroRepositorio libros, PrestamoRepositorio prestamos) {
        this.libros = libros;
        this.prestamos = prestamos;
    }

    public void registrarPrestamo(Prestamo prestamo) {
        // los datos se leen del repositorio en cada recomendación
    }

    public synchronized List<Libro> recomendar(long usuarioId, int cantidad) {
        if (cantidad < 1 || cantidad > MAX_RECOMENDACIONES) {
            throw new ErrorNegocio(400, "Se pueden pedir entre 1 y " + MAX_RECOMENDACIONES + " recomendaciones");
        }
        Map<Long, Set<Long>> historial = new HashMap<>();
        Map<Long, Integer> popularidad = new HashMap<>();
        for (Prestamo p : prestamos.todos()) {
            if (historial.computeIfAbsent(p.usuarioId(), k -> new HashSet<>()).add(p.libroId())) {
                popularidad.merge(p.libroId(), 1, Integer::sum);
            }
        }
        Set<Long> leidos = historial.getOrDefault(usuarioId, Set.of());
        Map<Long, Integer> puntaje = new HashMap<>();
        for (Set<Long> otros : historial.values()) {
            for (Long leido : leidos) {
                if (otros.contains(leido)) {
                    for (Long candidato : otros) {
                        if (!candidato.equals(leido)) {
                            puntaje.merge(candidato, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        LinkedHashSet<Long> orden = new LinkedHashSet<>();
        puntaje.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .forEach(e -> orden.add(e.getKey()));
        List<Libro> catalogo = libros.todos();
        catalogo.sort(Comparator.<Libro>comparingInt(l -> -popularidad.getOrDefault(l.id(), 0))
                .thenComparingLong(Libro::id));
        Set<String> categorias = new HashSet<>();
        for (Long id : leidos) {
            libros.porId(id).ifPresent(l -> categorias.add(l.categoria()));
        }
        for (Libro libro : catalogo) {
            if (categorias.contains(libro.categoria())) {
                orden.add(libro.id());
            }
        }
        for (Libro libro : catalogo) {
            orden.add(libro.id());
        }
        List<Libro> resultado = new ArrayList<>();
        for (Long id : orden) {
            libros.porId(id).filter(l -> l.disponibles() > 0).ifPresent(resultado::add);
            if (resultado.size() == cantidad) {
                break;
            }
        }
        return resultado;
    }
}

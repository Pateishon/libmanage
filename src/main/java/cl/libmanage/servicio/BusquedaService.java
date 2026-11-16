package cl.libmanage.servicio;

import cl.libmanage.dominio.Libro;
import cl.libmanage.repositorio.LibroRepositorio;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Búsqueda avanzada: recorre el catálogo completo y compara cada libro con los criterios. */
public final class BusquedaService {
    private final LibroRepositorio libros;

    public BusquedaService(LibroRepositorio libros) {
        this.libros = libros;
    }

    public void indexar(Libro libro) {
        // sin índice: cada búsqueda lee el catálogo completo
    }

    public void quitar(long libroId) {
        // sin índice
    }

    public synchronized ResultadoBusqueda buscar(CriteriosBusqueda criterios) {
        criterios.validar();
        List<String> palabras = Texto.palabras(criterios.texto());
        String autor = Texto.normalizar(criterios.autor());
        String categoria = Texto.normalizar(criterios.categoria());
        List<Libro> coincidencias = new ArrayList<>();
        for (Libro libro : libros.todos()) {
            String todo = Texto.normalizar(libro.titulo() + " " + libro.autor() + " " + libro.categoria() + " "
                    + libro.isbn());
            List<String> palabrasLibro = Texto.palabras(todo);
            boolean coincide = true;
            for (String palabra : palabras) {
                if (palabrasLibro.stream().noneMatch(p -> p.startsWith(palabra))) {
                    coincide = false;
                }
            }
            if (!coincide) {
                continue;
            }
            if (!autor.isEmpty() && !Texto.normalizar(libro.autor()).contains(autor)) {
                continue;
            }
            if (!categoria.isEmpty() && !Texto.normalizar(libro.categoria()).equals(categoria)) {
                continue;
            }
            if (criterios.anioDesde() != null && libro.anio() < criterios.anioDesde()) {
                continue;
            }
            if (criterios.anioHasta() != null && libro.anio() >= criterios.anioHasta()) {
                continue;
            }
            if (criterios.soloDisponibles() && libro.disponibles() == 0) {
                continue;
            }
            coincidencias.add(libro);
        }
        coincidencias.sort(comparador(criterios.orden(), palabras));
        int desde = Math.min((criterios.pagina() - 1) * criterios.tamano(), coincidencias.size());
        int hasta = Math.min(desde + criterios.tamano(), coincidencias.size());
        return new ResultadoBusqueda(List.copyOf(coincidencias.subList(desde, hasta)), coincidencias.size(),
                criterios.pagina(), criterios.tamano());
    }

    private static int relevancia(Libro libro, List<String> palabras) {
        int puntos = 0;
        for (String palabra : palabras) {
            if (Texto.normalizar(libro.titulo()).contains(palabra)) {
                puntos += 2;
            }
            if (Texto.normalizar(libro.autor()).contains(palabra)) {
                puntos += 1;
            }
        }
        return puntos;
    }

    private static Comparator<Libro> comparador(CriteriosBusqueda.Orden orden, List<String> palabras) {
        Comparator<Libro> porTitulo = Comparator.comparing(Libro::titulo).thenComparingLong(Libro::id);
        return switch (orden == null ? CriteriosBusqueda.Orden.RELEVANCIA : orden) {
            case TITULO -> porTitulo;
            case ANIO_DESC -> Comparator.comparingInt(Libro::anio).reversed().thenComparing(porTitulo);
            case RELEVANCIA -> Comparator.<Libro>comparingInt(l -> -relevancia(l, palabras)).thenComparing(porTitulo);
        };
    }
}

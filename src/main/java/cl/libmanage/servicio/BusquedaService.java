package cl.libmanage.servicio;

import cl.libmanage.dominio.Libro;
import cl.libmanage.repositorio.LibroRepositorio;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Búsqueda avanzada con índice invertido: cada palabra (normalizada) apunta a los libros que la
 * contienen. Las palabras de la consulta se buscan por prefijo y se combinan con Y lógico.
 */
public final class BusquedaService {
    private record Candidato(Libro libro, int puntaje) {
    }

    private record Entrada(Libro libro, String tituloNormalizado, String autorNormalizado, String categoriaNormalizada) {
    }

    private final LibroRepositorio libros;
    private final NavigableMap<String, Set<Long>> indice = new ConcurrentSkipListMap<>();
    private final Map<Long, Entrada> entradas = new ConcurrentHashMap<>();

    public BusquedaService(LibroRepositorio libros) {
        this.libros = libros;
        libros.todos().forEach(this::indexar);
    }

    public void indexar(Libro libro) {
        quitar(libro.id());
        Entrada entrada = new Entrada(libro, Texto.normalizar(libro.titulo()), Texto.normalizar(libro.autor()),
                Texto.normalizar(libro.categoria()));
        entradas.put(libro.id(), entrada);
        Set<String> palabras = new HashSet<>(Texto.palabras(libro.titulo() + " " + libro.autor() + " "
                + libro.categoria() + " " + libro.isbn()));
        for (String palabra : palabras) {
            indice.computeIfAbsent(palabra, k -> ConcurrentHashMap.newKeySet()).add(libro.id());
        }
    }

    public void quitar(long libroId) {
        Entrada anterior = entradas.remove(libroId);
        if (anterior == null) {
            return;
        }
        for (String palabra : Texto.palabras(anterior.libro().titulo() + " " + anterior.libro().autor() + " "
                + anterior.libro().categoria() + " " + anterior.libro().isbn())) {
            Set<Long> ids = indice.get(palabra);
            if (ids != null) {
                ids.remove(libroId);
            }
        }
    }

    public ResultadoBusqueda buscar(CriteriosBusqueda criterios) {
        criterios.validar();
        List<String> palabras = Texto.palabras(criterios.texto());
        Set<Long> candidatos = palabras.isEmpty() ? entradas.keySet() : interseccion(palabras);
        String autor = Texto.normalizar(criterios.autor());
        String categoria = Texto.normalizar(criterios.categoria());

        // Solo se ordenan los primeros pagina*tamano resultados con un montículo acotado (O(n log k))
        // en vez de ordenar todas las coincidencias: era el principal costo de CPU bajo carga (perfil JFR).
        Comparator<Candidato> orden = comparador(criterios.orden());
        long limite = (long) criterios.pagina() * criterios.tamano();
        PriorityQueue<Candidato> mejores = new PriorityQueue<>(orden.reversed());
        int total = 0;
        for (Long id : candidatos) {
            Entrada entrada = entradas.get(id);
            if (entrada == null || !cumple(entrada, autor, categoria, criterios)) {
                continue;
            }
            Optional<Libro> actual = libros.porId(id);  // disponibilidad al día
            if (actual.isEmpty() || (criterios.soloDisponibles() && actual.get().disponibles() == 0)) {
                continue;
            }
            total++;
            Candidato candidato = new Candidato(actual.get(), relevancia(entrada, palabras));
            if (mejores.size() < limite) {
                mejores.add(candidato);
            } else if (orden.compare(candidato, mejores.peek()) < 0) {
                mejores.poll();
                mejores.add(candidato);
            }
        }
        List<Candidato> primeros = new ArrayList<>(mejores);
        primeros.sort(orden);
        int desde = (int) Math.min((long) (criterios.pagina() - 1) * criterios.tamano(), primeros.size());
        List<Libro> pagina = primeros.subList(desde, primeros.size()).stream().map(Candidato::libro).toList();
        return new ResultadoBusqueda(pagina, total, criterios.pagina(), criterios.tamano());
    }

    private Set<Long> interseccion(List<String> palabras) {
        Set<Long> resultado = null;
        for (String palabra : palabras) {
            Set<Long> conPrefijo = new HashSet<>();
            for (Set<Long> ids : indice.subMap(palabra, true, palabra + Character.MAX_VALUE, false).values()) {
                conPrefijo.addAll(ids);
            }
            if (resultado == null) {
                resultado = conPrefijo;
            } else {
                resultado.retainAll(conPrefijo);
            }
            if (resultado.isEmpty()) {
                break;
            }
        }
        return resultado;
    }

    private static boolean cumple(Entrada entrada, String autor, String categoria, CriteriosBusqueda criterios) {
        Libro libro = entrada.libro();
        if (!autor.isEmpty() && !entrada.autorNormalizado().contains(autor)) {
            return false;
        }
        if (!categoria.isEmpty() && !entrada.categoriaNormalizada().equals(categoria)) {
            return false;
        }
        if (criterios.anioDesde() != null && libro.anio() < criterios.anioDesde()) {
            return false;
        }
        return criterios.anioHasta() == null || libro.anio() <= criterios.anioHasta();
    }

    private static int relevancia(Entrada entrada, List<String> palabras) {
        int puntos = 0;
        for (String palabra : palabras) {
            if (entrada.tituloNormalizado().contains(palabra)) {
                puntos += 2;
            }
            if (entrada.autorNormalizado().contains(palabra)) {
                puntos += 1;
            }
        }
        return puntos;
    }

    private static Comparator<Candidato> comparador(CriteriosBusqueda.Orden orden) {
        Comparator<Candidato> porTitulo = Comparator.<Candidato, String>comparing(c -> c.libro().titulo())
                .thenComparingLong(c -> c.libro().id());
        return switch (orden == null ? CriteriosBusqueda.Orden.RELEVANCIA : orden) {
            case TITULO -> porTitulo;
            case ANIO_DESC -> Comparator.<Candidato>comparingInt(c -> -c.libro().anio()).thenComparing(porTitulo);
            case RELEVANCIA -> Comparator.<Candidato>comparingInt(c -> -c.puntaje()).thenComparing(porTitulo);
        };
    }
}

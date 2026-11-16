package cl.libmanage.dominio;

/** Libro del inventario. {@code disponibles} siempre está entre 0 y {@code ejemplares}. */
public record Libro(long id, String isbn, String titulo, String autor, String categoria,
                    int anio, int ejemplares, int disponibles) {

    public int prestados() {
        return ejemplares - disponibles;
    }

    public Libro conId(long nuevoId) {
        return new Libro(nuevoId, isbn, titulo, autor, categoria, anio, ejemplares, disponibles);
    }

    public Libro conDisponibles(int nuevos) {
        return new Libro(id, isbn, titulo, autor, categoria, anio, ejemplares, nuevos);
    }

    public Libro conEjemplares(int nuevoTotal) {
        return new Libro(id, isbn, titulo, autor, categoria, anio, nuevoTotal, nuevoTotal - prestados());
    }
}

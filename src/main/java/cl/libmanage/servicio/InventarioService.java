package cl.libmanage.servicio;

import cl.libmanage.dominio.ErrorNegocio;
import cl.libmanage.dominio.Libro;
import cl.libmanage.repositorio.LibroRepositorio;
import java.time.Clock;
import java.time.Year;

/** Gestión del inventario (solo administradores). */
public final class InventarioService {
    public static final int MAX_EJEMPLARES = 100;
    public static final int MAX_LARGO = 200;

    private final LibroRepositorio libros;
    private final BusquedaService busqueda;
    private final Clock reloj;

    public InventarioService(LibroRepositorio libros, BusquedaService busqueda, Clock reloj) {
        this.libros = libros;
        this.busqueda = busqueda;
        this.reloj = reloj;
    }

    public synchronized Libro agregar(String isbn, String titulo, String autor, String categoria, int anio,
                                      int ejemplares) {
        if (!Isbn.esValido(isbn)) {
            throw new ErrorNegocio(400, "ISBN-13 inválido");
        }
        validarTexto(titulo, "título");
        validarTexto(autor, "autor");
        validarTexto(categoria, "categoría");
        int anioMaximo = Year.now(reloj).getValue() + 1;
        if (anio < CriteriosBusqueda.ANIO_MINIMO || anio > anioMaximo) {
            throw new ErrorNegocio(400, "El año debe estar entre " + CriteriosBusqueda.ANIO_MINIMO + " y " + anioMaximo);
        }
        validarEjemplares(ejemplares);
        String normalizado = Isbn.normalizar(isbn);
        if (libros.porIsbn(normalizado).isPresent()) {
            throw new ErrorNegocio(409, "Ya existe un libro con ese ISBN");
        }
        Libro nuevo = libros.guardar(new Libro(0, normalizado, titulo.trim(), autor.trim(), categoria.trim(), anio,
                ejemplares, ejemplares));
        busqueda.indexar(nuevo);
        return nuevo;
    }

    public synchronized Libro ajustarEjemplares(long libroId, int nuevoTotal) {
        validarEjemplares(nuevoTotal);
        Libro libro = libros.porId(libroId).orElseThrow(() -> new ErrorNegocio(404, "Libro no encontrado"));
        if (nuevoTotal < libro.prestados()) {
            throw new ErrorNegocio(409, "Hay " + libro.prestados() + " ejemplares prestados; el total no puede ser menor");
        }
        Libro actualizado = libros.guardar(libro.conEjemplares(nuevoTotal));
        busqueda.indexar(actualizado);
        return actualizado;
    }

    public synchronized void darDeBaja(long libroId) {
        Libro libro = libros.porId(libroId).orElseThrow(() -> new ErrorNegocio(404, "Libro no encontrado"));
        if (libro.prestados() > 0) {
            throw new ErrorNegocio(409, "No se puede dar de baja un libro con préstamos activos");
        }
        libros.eliminar(libroId);
        busqueda.quitar(libroId);
    }

    private static void validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank() || valor.length() > MAX_LARGO) {
            throw new ErrorNegocio(400, "El campo " + campo + " es obligatorio y admite hasta " + MAX_LARGO + " caracteres");
        }
    }

    private static void validarEjemplares(int ejemplares) {
        if (ejemplares < 1 || ejemplares > MAX_EJEMPLARES) {
            throw new ErrorNegocio(400, "La cantidad de ejemplares debe estar entre 1 y " + MAX_EJEMPLARES);
        }
    }
}

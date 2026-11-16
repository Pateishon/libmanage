package cl.libmanage.servicio;

import cl.libmanage.dominio.Libro;
import java.util.List;

public record ResultadoBusqueda(List<Libro> libros, int total, int pagina, int tamano) {
}

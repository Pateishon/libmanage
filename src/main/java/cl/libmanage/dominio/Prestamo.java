package cl.libmanage.dominio;

import java.time.LocalDate;

/** Préstamo de un libro. {@code devolucion} es null mientras el préstamo está activo. */
public record Prestamo(long id, long usuarioId, long libroId, LocalDate inicio, LocalDate limite,
                       LocalDate devolucion) {

    public boolean activo() {
        return devolucion == null;
    }

    public boolean vencido(LocalDate hoy) {
        return activo() && hoy.isAfter(limite);
    }

    public Prestamo conId(long nuevoId) {
        return new Prestamo(nuevoId, usuarioId, libroId, inicio, limite, devolucion);
    }

    public Prestamo devuelto(LocalDate fecha) {
        return new Prestamo(id, usuarioId, libroId, inicio, limite, fecha);
    }
}

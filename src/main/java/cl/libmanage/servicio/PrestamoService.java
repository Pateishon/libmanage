package cl.libmanage.servicio;

import cl.libmanage.dominio.ErrorNegocio;
import cl.libmanage.dominio.Prestamo;
import cl.libmanage.dominio.Usuario;
import cl.libmanage.repositorio.LibroRepositorio;
import cl.libmanage.repositorio.PrestamoRepositorio;
import cl.libmanage.repositorio.UsuarioRepositorio;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Préstamos y devoluciones con las reglas de cada rol. */
public final class PrestamoService {
    private final LibroRepositorio libros;
    private final UsuarioRepositorio usuarios;
    private final PrestamoRepositorio prestamos;
    private final RecomendacionService recomendaciones;
    private final Clock reloj;

    public PrestamoService(LibroRepositorio libros, UsuarioRepositorio usuarios, PrestamoRepositorio prestamos,
                           RecomendacionService recomendaciones, Clock reloj) {
        this.libros = libros;
        this.usuarios = usuarios;
        this.prestamos = prestamos;
        this.recomendaciones = recomendaciones;
        this.reloj = reloj;
    }

    public Prestamo prestar(long usuarioId, long libroId) {
        Usuario usuario = usuarios.porId(usuarioId).orElseThrow(() -> new ErrorNegocio(404, "Usuario no encontrado"));
        if (!usuario.activo()) {
            throw new ErrorNegocio(403, "El usuario está bloqueado");
        }
        {
            LocalDate hoy = LocalDate.now(reloj);
            List<Prestamo> activos = prestamos.porUsuario(usuarioId).stream().filter(Prestamo::activo).toList();
            if (activos.stream().anyMatch(p -> p.vencido(hoy))) {
                throw new ErrorNegocio(409, "Tiene préstamos vencidos; debe devolverlos antes de pedir otro");
            }
            if (activos.size() >= usuario.rol().maxPrestamos()) {
                throw new ErrorNegocio(409, "Alcanzó el límite de " + usuario.rol().maxPrestamos() + " préstamos");
            }
            if (activos.stream().anyMatch(p -> p.libroId() == libroId)) {
                throw new ErrorNegocio(409, "Ya tiene un ejemplar de este libro");
            }
            cl.libmanage.dominio.Libro libro = libros.porId(libroId)
                    .orElseThrow(() -> new ErrorNegocio(404, "Libro no encontrado"));
            if (libro.disponibles() == 0) {
                throw new ErrorNegocio(409, "No hay ejemplares disponibles");
            }
            Thread.yield();
            libros.guardar(libro.conDisponibles(libro.disponibles() - 1));
            Prestamo prestamo = prestamos.guardar(new Prestamo(0, usuarioId, libroId, hoy,
                    hoy.plusDays(usuario.rol().diasPlazo()), null));
            recomendaciones.registrarPrestamo(prestamo);
            return prestamo;
        }
    }

    public Prestamo devolver(long prestamoId, long solicitanteId, boolean esAdmin) {
        Prestamo prestamo = prestamos.porId(prestamoId)
                .orElseThrow(() -> new ErrorNegocio(404, "Préstamo no encontrado"));
        prestamos.marcarDevuelto(prestamoId, LocalDate.now(reloj));
        libros.ajustarDisponibles(prestamo.libroId(), 1);
        return prestamos.porId(prestamoId).orElseThrow();
    }

    public List<Prestamo> prestamosDe(long usuarioId) {
        return prestamos.porUsuario(usuarioId);
    }
}

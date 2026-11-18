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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Préstamos y devoluciones con las reglas de cada rol. */
public final class PrestamoService {
    private final LibroRepositorio libros;
    private final UsuarioRepositorio usuarios;
    private final PrestamoRepositorio prestamos;
    private final RecomendacionService recomendaciones;
    private final Clock reloj;
    private final Map<Long, Object> candadosPorUsuario = new ConcurrentHashMap<>();

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
        // Un candado por usuario evita que dos solicitudes simultáneas superen su límite.
        synchronized (candadosPorUsuario.computeIfAbsent(usuarioId, k -> new Object())) {
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
            if (libros.porId(libroId).isEmpty()) {
                throw new ErrorNegocio(404, "Libro no encontrado");
            }
            if (!libros.ajustarDisponibles(libroId, -1)) {  // atómico: evita prestar el mismo ejemplar dos veces
                throw new ErrorNegocio(409, "No hay ejemplares disponibles");
            }
            Prestamo prestamo = prestamos.guardar(new Prestamo(0, usuarioId, libroId, hoy,
                    hoy.plusDays(usuario.rol().diasPlazo()), null));
            recomendaciones.registrarPrestamo(prestamo);
            return prestamo;
        }
    }

    public Prestamo devolver(long prestamoId, long solicitanteId, boolean esAdmin) {
        Prestamo prestamo = prestamos.porId(prestamoId)
                .orElseThrow(() -> new ErrorNegocio(404, "Préstamo no encontrado"));
        if (!esAdmin && prestamo.usuarioId() != solicitanteId) {
            throw new ErrorNegocio(404, "Préstamo no encontrado");  // no revela préstamos ajenos
        }
        if (!prestamos.marcarDevuelto(prestamoId, LocalDate.now(reloj))) {
            throw new ErrorNegocio(409, "El préstamo ya fue devuelto");
        }
        libros.ajustarDisponibles(prestamo.libroId(), 1);
        return prestamos.porId(prestamoId).orElseThrow();
    }

    public List<Prestamo> prestamosDe(long usuarioId) {
        return prestamos.porUsuario(usuarioId);
    }
}

package cl.libmanage.web;

import cl.libmanage.repositorio.LibroRepositorio;
import cl.libmanage.repositorio.PrestamoRepositorio;
import cl.libmanage.repositorio.UsuarioRepositorio;
import cl.libmanage.seguridad.Contrasenas;
import cl.libmanage.seguridad.LimitadorIntentos;
import cl.libmanage.seguridad.TokenService;
import cl.libmanage.servicio.AutenticacionService;
import cl.libmanage.servicio.BusquedaService;
import cl.libmanage.servicio.InventarioService;
import cl.libmanage.servicio.PrestamoService;
import cl.libmanage.servicio.RecomendacionService;
import java.time.Clock;
import java.time.Duration;

/** Ensambla los componentes de LibManage (inyección de dependencias manual). */
public final class Aplicacion {
    public final LibroRepositorio libros;
    public final UsuarioRepositorio usuarios;
    public final PrestamoRepositorio prestamos;
    public final TokenService tokens;
    public final BusquedaService busqueda;
    public final RecomendacionService recomendaciones;
    public final InventarioService inventario;
    public final PrestamoService prestamosService;
    public final AutenticacionService autenticacion;

    public Aplicacion(LibroRepositorio libros, UsuarioRepositorio usuarios, PrestamoRepositorio prestamos,
                      Contrasenas contrasenas, byte[] claveTokens, Clock reloj) {
        this.libros = libros;
        this.usuarios = usuarios;
        this.prestamos = prestamos;
        this.tokens = new TokenService(claveTokens, reloj, Duration.ofMinutes(30));
        this.busqueda = new BusquedaService(libros);
        this.recomendaciones = new RecomendacionService(libros, prestamos);
        this.inventario = new InventarioService(libros, busqueda, reloj);
        this.prestamosService = new PrestamoService(libros, usuarios, prestamos, recomendaciones, reloj);
        this.autenticacion = new AutenticacionService(usuarios, contrasenas, tokens,
                new LimitadorIntentos(5, Duration.ofMinutes(15), reloj));
    }
}

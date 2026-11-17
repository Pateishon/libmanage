package cl.libmanage.soporte;

import cl.libmanage.dominio.Libro;
import cl.libmanage.dominio.Rol;
import cl.libmanage.dominio.Usuario;
import cl.libmanage.repositorio.LibroRepositorio;
import cl.libmanage.repositorio.LibroRepositorioMemoria;
import cl.libmanage.repositorio.PrestamoRepositorioMemoria;
import cl.libmanage.repositorio.UsuarioRepositorioMemoria;
import cl.libmanage.seguridad.CifradorDatos;
import cl.libmanage.seguridad.Contrasenas;
import cl.libmanage.servicio.Isbn;
import cl.libmanage.web.Aplicacion;
import cl.libmanage.web.Servidor;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;

/**
 * Datos de prueba conocidos: un catálogo pequeño, cuatro usuarios (estudiante, docente, administrador y
 * uno bloqueado) y un reloj controlado fijado en el lunes 2 de noviembre de 2026.
 */
public final class Escenario {
    public static final String CLAVE = "Clave-Segura-2026";
    public static final byte[] CLAVE_DATOS = relleno((byte) 7);
    public static final byte[] CLAVE_TOKENS = relleno((byte) 9);

    public final RelojControlado reloj = new RelojControlado(Instant.parse("2026-11-02T13:00:00Z"),
            ZoneId.of("America/Santiago"));
    public final Contrasenas contrasenas = new Contrasenas(1_000);  // pocas iteraciones: pruebas rápidas
    public final CifradorDatos cifrador = new CifradorDatos(CLAVE_DATOS);
    public final LibroRepositorio libros;
    public final UsuarioRepositorioMemoria usuarios = new UsuarioRepositorioMemoria(cifrador);
    public final PrestamoRepositorioMemoria prestamos = new PrestamoRepositorioMemoria();
    public final Usuario estudiante;
    public final Usuario docente;
    public final Usuario admin;
    public final Usuario bloqueado;
    public final Aplicacion app;
    private HttpServer servidor;

    public Escenario() {
        this(new LibroRepositorioMemoria());
    }

    public Escenario(LibroRepositorio repositorioLibros) {
        this.libros = repositorioLibros;
        String hash = contrasenas.hash(CLAVE);
        estudiante = usuarios.guardar(new Usuario(0, "Ana Torres", "ana@u.cl", "12.345.678-5", hash, Rol.ESTUDIANTE, true));
        docente = usuarios.guardar(new Usuario(0, "Luis Rojas", "luis@u.cl", "9.876.543-3", hash, Rol.DOCENTE, true));
        admin = usuarios.guardar(new Usuario(0, "Admin", "admin@u.cl", "11.111.111-1", hash, Rol.ADMIN, true));
        bloqueado = usuarios.guardar(new Usuario(0, "Bruno Bloq", "bruno@u.cl", "15.555.555-5", hash, Rol.ESTUDIANTE, false));
        libro("Ingeniería de software", "Ian Sommerville", "Informática", 2011, 3);   // id 1
        libro("Código limpio", "Robert C. Martin", "Informática", 2008, 2);           // id 2
        libro("Refactorización", "Martin Fowler", "Informática", 2018, 1);            // id 3
        libro("Patrones de diseño", "Erich Gamma", "Informática", 1994, 2);           // id 4
        libro("Cálculo de una variable", "James Stewart", "Matemática", 2012, 2);     // id 5
        libro("Álgebra lineal", "Gilbert Strang", "Matemática", 2016, 1);             // id 6
        libro("Cien años de soledad", "Gabriel García Márquez", "Literatura", 1967, 2); // id 7
        libro("Historia de Chile", "Sergio Villalobos", "Historia", 1974, 1);         // id 8
        app = new Aplicacion(libros, usuarios, prestamos, contrasenas, CLAVE_TOKENS, reloj);
    }

    private int correlativo = 0;

    public Libro libro(String titulo, String autor, String categoria, int anio, int ejemplares) {
        String isbn = Isbn.conVerificador("978" + (100_000_000 + correlativo++));
        return libros.guardar(new Libro(0, isbn, titulo, autor, categoria, anio, ejemplares, ejemplares));
    }

    /** Levanta la API en un puerto libre y devuelve un cliente HTTP hacia ella. */
    public ClienteHttp iniciarServidor() throws IOException {
        servidor = Servidor.iniciar(app, reloj, "127.0.0.1", 0);
        return new ClienteHttp("http://127.0.0.1:" + servidor.getAddress().getPort());
    }

    public void detener() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    public String token(Usuario usuario) {
        return app.tokens.emitir(usuario.id(), usuario.rol());
    }

    private static byte[] relleno(byte valor) {
        byte[] clave = new byte[32];
        Arrays.fill(clave, valor);
        return clave;
    }
}

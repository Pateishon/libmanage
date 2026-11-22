package cl.libmanage.web;

import cl.libmanage.repositorio.JdbcLibroRepositorio;
import cl.libmanage.repositorio.LibroRepositorio;
import cl.libmanage.repositorio.LibroRepositorioMemoria;
import cl.libmanage.repositorio.PrestamoRepositorioMemoria;
import cl.libmanage.repositorio.UsuarioRepositorioMemoria;
import cl.libmanage.seguridad.CifradorDatos;
import cl.libmanage.seguridad.Contrasenas;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/** Punto de entrada: levanta la API y la interfaz web. */
public final class Servidor {
    private static final Logger LOG = Logger.getLogger(Servidor.class.getName());

    private Servidor() {
    }

    /** Inicia el servidor HTTP; cada solicitud se atiende en un hilo virtual. */
    public static HttpServer iniciar(Aplicacion app, Clock reloj, String host, int puerto) throws IOException {
        // TCP_NODELAY: sin esto, el algoritmo de Nagle junto al ACK retardado del cliente agregaba
        // ~40 ms a cada respuesta (cuello de botella detectado en la prueba de carga).
        System.setProperty("sun.net.httpserver.nodelay", "true");
        HttpServer servidor = HttpServer.create(new InetSocketAddress(host, puerto), 512);
        servidor.createContext("/api/", new ApiHandler(app, reloj));
        servidor.createContext("/", new ArchivosEstaticos());
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        servidor.start();
        return servidor;
    }

    public static void main(String[] args) throws IOException {
        Clock reloj = Clock.systemDefaultZone();
        byte[] clave = clave("LIBMANAGE_CLAVE");
        byte[] claveTokens = clave("LIBMANAGE_CLAVE_TOKENS");
        String urlBd = System.getenv("LIBMANAGE_DB_URL");
        LibroRepositorio libros = urlBd == null ? new LibroRepositorioMemoria()
                : new JdbcLibroRepositorio(urlBd, System.getenv("LIBMANAGE_DB_USUARIO"), System.getenv("LIBMANAGE_DB_CLAVE"));
        int iteraciones = Integer.parseInt(entorno("LIBMANAGE_ITERACIONES", String.valueOf(Contrasenas.ITERACIONES_PRODUCCION)));
        Contrasenas contrasenas = new Contrasenas(iteraciones);
        UsuarioRepositorioMemoria usuarios = new UsuarioRepositorioMemoria(new CifradorDatos(clave));
        PrestamoRepositorioMemoria prestamos = new PrestamoRepositorioMemoria();
        if (urlBd == null) {
            DatosDemo.poblar(libros, usuarios, prestamos, contrasenas, reloj,
                    Integer.parseInt(entorno("LIBMANAGE_LIBROS", "2000")),
                    Integer.parseInt(entorno("LIBMANAGE_LECTORES", "300")));
        }
        Aplicacion app = new Aplicacion(libros, usuarios, prestamos, contrasenas, claveTokens, reloj);
        String host = entorno("LIBMANAGE_HOST", "127.0.0.1");
        int puerto = Integer.parseInt(entorno("LIBMANAGE_PUERTO", "8080"));
        iniciar(app, reloj, host, puerto);
        LOG.info("LibManage escuchando en http://" + host + ":" + puerto);
    }

    private static String entorno(String nombre, String defecto) {
        String valor = System.getenv(nombre);
        return valor == null || valor.isBlank() ? defecto : valor;
    }

    private static byte[] clave(String variable) {
        String valor = System.getenv(variable);
        if (valor != null) {
            return Base64.getDecoder().decode(valor);
        }
        LOG.warning(variable + " no definida: se usa una clave temporal (solo para desarrollo)");
        byte[] temporal = new byte[32];
        new SecureRandom().nextBytes(temporal);
        return temporal;
    }
}

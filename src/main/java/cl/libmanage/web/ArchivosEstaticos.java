package cl.libmanage.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/** Sirve la interfaz web. Solo entrega una lista cerrada de archivos (evita recorrer directorios). */
public final class ArchivosEstaticos implements HttpHandler {
    private static final Map<String, String[]> ARCHIVOS = Map.of(
            "/", new String[] {"index.html", "text/html; charset=utf-8"},
            "/index.html", new String[] {"index.html", "text/html; charset=utf-8"},
            "/app.js", new String[] {"app.js", "text/javascript; charset=utf-8"},
            "/estilos.css", new String[] {"estilos.css", "text/css; charset=utf-8"});

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String[] archivo = ARCHIVOS.get(ex.getRequestURI().getPath());
        byte[] cuerpo = null;
        if (archivo != null) {
            try (InputStream recurso = ArchivosEstaticos.class.getResourceAsStream("/static/" + archivo[0])) {
                cuerpo = recurso == null ? null : recurso.readAllBytes();
            }
        }
        CabecerasSeguridad.aplicar(ex.getResponseHeaders());
        if (cuerpo == null) {
            cuerpo = "No encontrado".getBytes();
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(404, cuerpo.length);
        } else {
            ex.getResponseHeaders().set("Content-Type", archivo[1]);
            ex.sendResponseHeaders(200, cuerpo.length);
        }
        try (OutputStream salida = ex.getResponseBody()) {
            salida.write(cuerpo);
        }
    }
}

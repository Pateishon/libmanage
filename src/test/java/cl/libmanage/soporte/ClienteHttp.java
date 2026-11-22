package cl.libmanage.soporte;

import cl.libmanage.web.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/** Cliente HTTP de caja negra: solo conoce la API pública, no las clases internas. */
public final class ClienteHttp {
    public record Respuesta(int estado, String cuerpo, java.net.http.HttpHeaders cabeceras) {
        public Map<String, Object> json() {
            return Json.leerObjeto(cuerpo);
        }

        public String cabecera(String nombre) {
            return cabeceras.firstValue(nombre).orElse(null);
        }
    }

    private final String base;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .proxy(HttpClient.Builder.NO_PROXY).build();

    public ClienteHttp(String base) {
        this.base = base;
    }

    public String base() {
        return base;
    }

    public Respuesta get(String ruta, String token) {
        return enviar(peticion(ruta, token).GET().build());
    }

    public Respuesta post(String ruta, String token, String cuerpo) {
        return enviar(peticion(ruta, token).POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build());
    }

    public Respuesta put(String ruta, String token, String cuerpo) {
        return enviar(peticion(ruta, token).PUT(HttpRequest.BodyPublishers.ofString(cuerpo)).build());
    }

    public Respuesta delete(String ruta, String token) {
        return enviar(peticion(ruta, token).DELETE().build());
    }

    private HttpRequest.Builder peticion(String ruta, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + ruta)).header("Content-Type", "application/json");
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return b;
    }

    private Respuesta enviar(HttpRequest peticion) {
        try {
            HttpResponse<String> r = http.send(peticion, HttpResponse.BodyHandlers.ofString());
            return new Respuesta(r.statusCode(), r.body(), r.headers());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

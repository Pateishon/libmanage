package cl.libmanage.web;

import com.sun.net.httpserver.Headers;

/** Cabeceras HTTP de seguridad comunes a todas las respuestas (OWASP Secure Headers). */
final class CabecerasSeguridad {
    private CabecerasSeguridad() {
    }

    static void aplicar(Headers cabeceras) {
        cabeceras.set("X-Content-Type-Options", "nosniff");
        cabeceras.set("X-Frame-Options", "DENY");
        cabeceras.set("Referrer-Policy", "no-referrer");
        cabeceras.set("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'none'");
    }
}

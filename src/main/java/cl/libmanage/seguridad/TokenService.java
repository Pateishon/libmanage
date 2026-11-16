package cl.libmanage.seguridad;

import cl.libmanage.dominio.Rol;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Tokens de sesión firmados con HMAC-SHA256: base64url(usuario|rol|expiración).base64url(firma). */
public final class TokenService {
    private final SecretKeySpec clave;
    private final Clock reloj;
    private final Duration duracion;

    public record Sesion(long usuarioId, Rol rol) {
        public boolean esAdmin() {
            return rol == Rol.ADMIN;
        }
    }

    public TokenService(byte[] clave, Clock reloj, Duration duracion) {
        this.clave = new SecretKeySpec(clave, "HmacSHA256");
        this.reloj = reloj;
        this.duracion = duracion;
    }

    public String emitir(long usuarioId, Rol rol) {
        long expira = reloj.instant().plus(duracion).getEpochSecond();
        String datos = usuarioId + "|" + rol.name() + "|" + expira;
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String carga = b64.encodeToString(datos.getBytes(StandardCharsets.UTF_8));
        return carga + "." + b64.encodeToString(firmar(carga));
    }

    public Optional<Sesion> validar(String token) {
        if (token == null || token.length() > 512) {
            return Optional.empty();
        }
        int punto = token.indexOf('.');
        if (punto < 1) {
            return Optional.empty();
        }
        String carga = token.substring(0, punto);
        try {
            byte[] firma = Base64.getUrlDecoder().decode(token.substring(punto + 1));
            if (!MessageDigest.isEqual(firma, firmar(carga))) {
                return Optional.empty();
            }
            String[] partes = new String(Base64.getUrlDecoder().decode(carga), StandardCharsets.UTF_8).split("\\|");
            if (partes.length != 3 || reloj.instant().getEpochSecond() >= Long.parseLong(partes[2])) {
                return Optional.empty();
            }
            return Optional.of(new Sesion(Long.parseLong(partes[0]), Rol.valueOf(partes[1])));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] firmar(String carga) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(clave);
            return mac.doFinal(carga.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}

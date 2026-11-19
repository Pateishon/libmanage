package cl.libmanage.seguridad;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hash de contraseñas con PBKDF2-HMAC-SHA256 y sal aleatoria de 16 bytes.
 * Formato: pbkdf2$iteraciones$sal$hash. En producción se usan 600.000 iteraciones (OWASP).
 */
public final class Contrasenas {
    public static final int ITERACIONES_PRODUCCION = 600_000;
    private static final int LARGO_HASH_BITS = 256;
    private final int iteraciones;
    private final SecureRandom azar = new SecureRandom();

    public Contrasenas(int iteraciones) {
        this.iteraciones = iteraciones;
    }

    public String hash(String contrasena) {
        byte[] sal = new byte[16];
        azar.nextBytes(sal);
        byte[] derivado = derivar(contrasena, sal, iteraciones);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2$" + iteraciones + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(derivado);
    }

    public boolean verificar(String contrasena, String almacenado) {
        String[] partes = almacenado == null ? new String[0] : almacenado.split("\\$");
        if (partes.length != 4 || !"pbkdf2".equals(partes[0])) {
            return false;
        }
        Base64.Decoder b64 = Base64.getDecoder();
        byte[] esperado = b64.decode(partes[3]);
        byte[] calculado = derivar(contrasena, b64.decode(partes[2]), Integer.parseInt(partes[1]));
        return MessageDigest.isEqual(esperado, calculado);  // comparación en tiempo constante
    }

    private static byte[] derivar(String contrasena, byte[] sal, int iteraciones) {
        try {
            PBEKeySpec spec = new PBEKeySpec(contrasena.toCharArray(), sal, iteraciones, LARGO_HASH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}

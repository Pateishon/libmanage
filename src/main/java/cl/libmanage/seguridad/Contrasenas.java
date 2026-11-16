package cl.libmanage.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Hash de contraseñas. */
public final class Contrasenas {
    public static final int ITERACIONES_PRODUCCION = 1;

    public Contrasenas(int iteraciones) {
        // parámetro no utilizado
    }

    public String hash(String contrasena) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(contrasena.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean verificar(String contrasena, String almacenado) {
        return almacenado != null && hash(contrasena).equals(almacenado);
    }
}

package cl.libmanage.seguridad;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifra datos personales en reposo con AES-256-GCM (confidencialidad e integridad).
 * Cada valor usa un IV aleatorio de 12 bytes; el resultado es Base64(IV || texto cifrado || etiqueta).
 * {@link #indiceCiego(String)} permite buscar por email sin guardarlo en claro (HMAC-SHA256).
 */
public final class CifradorDatos {
    private static final int LARGO_IV = 12;
    private static final int BITS_ETIQUETA = 128;
    private final SecretKeySpec claveCifrado;
    private final SecretKeySpec claveIndice;
    private final SecureRandom azar = new SecureRandom();

    public CifradorDatos(byte[] clave) {
        if (clave.length != 32) {
            throw new IllegalArgumentException("La clave debe tener 32 bytes (AES-256)");
        }
        this.claveCifrado = new SecretKeySpec(clave, "AES");
        this.claveIndice = new SecretKeySpec(derivar(clave, "indice"), "HmacSHA256");
    }

    public String cifrar(String textoPlano) {
        try {
            byte[] iv = new byte[LARGO_IV];
            azar.nextBytes(iv);
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.ENCRYPT_MODE, claveCifrado, new GCMParameterSpec(BITS_ETIQUETA, iv));
            byte[] cifrado = cifrador.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length)
                    .put(iv).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el dato", e);
        }
    }

    public String descifrar(String almacenado) {
        try {
            byte[] datos = Base64.getDecoder().decode(almacenado);
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.DECRYPT_MODE, claveCifrado,
                    new GCMParameterSpec(BITS_ETIQUETA, datos, 0, LARGO_IV));
            byte[] plano = cifrador.doFinal(datos, LARGO_IV, datos.length - LARGO_IV);
            return new String(plano, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Dato cifrado inválido o alterado", e);
        }
    }

    public String indiceCiego(String valor) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(claveIndice);
            return HexFormat.of().formatHex(mac.doFinal(valor.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] derivar(byte[] clave, String proposito) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return mac.doFinal(proposito.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}

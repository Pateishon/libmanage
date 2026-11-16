package cl.libmanage.servicio;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalización de texto para búsquedas: minúsculas, sin tildes y separado en palabras. */
public final class Texto {
    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");

    private Texto() {
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = MARCAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        return NO_ALFANUMERICO.matcher(sinTildes.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    public static List<String> palabras(String texto) {
        String normalizado = normalizar(texto);
        return normalizado.isEmpty() ? List.of() : Arrays.asList(normalizado.split(" "));
    }
}

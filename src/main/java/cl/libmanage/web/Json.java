package cl.libmanage.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serializador y analizador JSON mínimo (sin dependencias externas). */
public final class Json {
    private final String texto;
    private int pos;

    private Json(String texto) {
        this.texto = texto;
    }

    public static String escribir(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(valor, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> leerObjeto(String texto) {
        Json analizador = new Json(texto == null ? "" : texto);
        analizador.espacios();
        Object valor = analizador.valor();
        analizador.espacios();
        if (analizador.pos != analizador.texto.length() || !(valor instanceof Map)) {
            throw new IllegalArgumentException("Se esperaba un objeto JSON");
        }
        return (Map<String, Object>) valor;
    }

    private static void escribir(Object valor, StringBuilder sb) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof String s) {
            escribirTexto(s, sb);
        } else if (valor instanceof Number || valor instanceof Boolean) {
            sb.append(valor);
        } else if (valor instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primero) {
                    sb.append(',');
                }
                primero = false;
                escribirTexto(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escribir(e.getValue(), sb);
            }
            sb.append('}');
        } else if (valor instanceof Iterable<?> lista) {
            sb.append('[');
            boolean primero = true;
            for (Object elemento : lista) {
                if (!primero) {
                    sb.append(',');
                }
                primero = false;
                escribir(elemento, sb);
            }
            sb.append(']');
        } else {
            escribirTexto(valor.toString(), sb);
        }
    }

    /** Escapa también &lt; y &gt; para que el JSON nunca pueda interpretarse como HTML. */
    private static void escribirTexto(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003c");
                case '>' -> sb.append("\\u003e");
                case '&' -> sb.append("\\u0026");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private Object valor() {
        if (pos >= texto.length()) {
            throw new IllegalArgumentException("JSON incompleto");
        }
        char c = texto.charAt(pos);
        return switch (c) {
            case '{' -> objeto();
            case '[' -> arreglo();
            case '"' -> cadena();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> numero();
        };
    }

    private Map<String, Object> objeto() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        pos++;
        espacios();
        if (siguiente('}')) {
            return mapa;
        }
        do {
            espacios();
            String clave = cadena();
            espacios();
            esperar(':');
            espacios();
            mapa.put(clave, valor());
            espacios();
        } while (siguiente(','));
        esperar('}');
        return mapa;
    }

    private List<Object> arreglo() {
        List<Object> lista = new ArrayList<>();
        pos++;
        espacios();
        if (siguiente(']')) {
            return lista;
        }
        do {
            espacios();
            lista.add(valor());
            espacios();
        } while (siguiente(','));
        esperar(']');
        return lista;
    }

    private String cadena() {
        esperar('"');
        StringBuilder sb = new StringBuilder();
        while (pos < texto.length()) {
            char c = texto.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= texto.length()) {
                    break;
                }
                char e = texto.charAt(pos++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > texto.length()) {
                            throw new IllegalArgumentException("Escape unicode incompleto");
                        }
                        sb.append((char) Integer.parseInt(texto.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("Cadena JSON sin cerrar");
    }

    private Object numero() {
        int inicio = pos;
        while (pos < texto.length() && "+-0123456789.eE".indexOf(texto.charAt(pos)) >= 0) {
            pos++;
        }
        String n = texto.substring(inicio, pos);
        if (n.isEmpty()) {
            throw new IllegalArgumentException("Valor JSON inválido");
        }
        if (n.contains(".") || n.contains("e") || n.contains("E")) {
            return Double.parseDouble(n);
        }
        return Long.parseLong(n);
    }

    private Object literal(String palabra, Object valor) {
        if (!texto.startsWith(palabra, pos)) {
            throw new IllegalArgumentException("Valor JSON inválido");
        }
        pos += palabra.length();
        return valor;
    }

    private void espacios() {
        while (pos < texto.length() && Character.isWhitespace(texto.charAt(pos))) {
            pos++;
        }
    }

    private boolean siguiente(char c) {
        if (pos < texto.length() && texto.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private void esperar(char c) {
        if (!siguiente(c)) {
            throw new IllegalArgumentException("Se esperaba '" + c + "' en la posición " + pos);
        }
    }
}

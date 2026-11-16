package cl.libmanage.servicio;

/** Validación de ISBN-13 (prefijo 978/979 y dígito verificador módulo 10). */
public final class Isbn {
    private Isbn() {
    }

    public static String normalizar(String isbn) {
        return isbn == null ? "" : isbn.replace("-", "").replace(" ", "");
    }

    public static boolean esValido(String isbn) {
        String digitos = normalizar(isbn);
        if (digitos.length() != 13 || !digitos.chars().allMatch(Character::isDigit)) {
            return false;
        }
        if (!digitos.startsWith("978") && !digitos.startsWith("979")) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < 12; i++) {
            int digito = digitos.charAt(i) - '0';
            suma += i % 2 == 0 ? digito : digito * 3;
        }
        int verificador = (10 - suma % 10) % 10;
        return verificador == digitos.charAt(12) - '0';
    }

    /** Completa 12 dígitos con su dígito verificador (usado para generar datos de prueba). */
    public static String conVerificador(String doceDigitos) {
        int suma = 0;
        for (int i = 0; i < 12; i++) {
            int digito = doceDigitos.charAt(i) - '0';
            suma += i % 2 == 0 ? digito : digito * 3;
        }
        return doceDigitos + (10 - suma % 10) % 10;
    }
}

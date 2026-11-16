package cl.libmanage.seguridad;

/** Oculta parcialmente datos personales antes de mostrarlos (principio de minimización). */
public final class Enmascarador {
    private Enmascarador() {
    }

    /** "12.345.678-5" → "**.***.678-5". */
    public static String rut(String rut) {
        if (rut == null || rut.length() < 5) {
            return "***";
        }
        int visibles = 5;
        String inicio = rut.substring(0, rut.length() - visibles).replaceAll("\\d", "*");
        return inicio + rut.substring(rut.length() - visibles);
    }

    /** "ana.torres@uautonoma.cl" → "a***@uautonoma.cl". */
    public static String email(String email) {
        if (email == null) {
            return "***";
        }
        int arroba = email.indexOf('@');
        if (arroba < 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(arroba);
    }
}

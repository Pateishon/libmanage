package cl.libmanage.dominio;

/** Rol del usuario, con sus reglas de préstamo. */
public enum Rol {
    ESTUDIANTE(3, 7),
    DOCENTE(5, 14),
    ADMIN(5, 14);

    private final int maxPrestamos;
    private final int diasPlazo;

    Rol(int maxPrestamos, int diasPlazo) {
        this.maxPrestamos = maxPrestamos;
        this.diasPlazo = diasPlazo;
    }

    public int maxPrestamos() {
        return maxPrestamos;
    }

    public int diasPlazo() {
        return diasPlazo;
    }
}

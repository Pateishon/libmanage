package cl.libmanage.dominio;

/** Error de una regla de negocio; {@code estado} es el código HTTP que verá el cliente. */
public class ErrorNegocio extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int estado;

    public ErrorNegocio(int estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public int estado() {
        return estado;
    }
}

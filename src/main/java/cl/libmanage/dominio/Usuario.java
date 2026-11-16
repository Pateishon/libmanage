package cl.libmanage.dominio;

/** Usuario del sistema. Email y RUT son datos personales: se cifran al almacenarse. */
public record Usuario(long id, String nombre, String email, String rut, String hashContrasena,
                      Rol rol, boolean activo) {

    public Usuario conId(long nuevoId) {
        return new Usuario(nuevoId, nombre, email, rut, hashContrasena, rol, activo);
    }
}

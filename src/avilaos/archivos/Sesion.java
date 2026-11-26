package avilaos.archivos;

/** Quién está usando el sistema: el modo (administrador o usuario) y el nombre del usuario. */
public record Sesion(ModoUsuario modo, String usuario) {

    /** El nombre del administrador, dueño de los archivos del sistema. */
    public static final String ADMIN = "admin";

    /** Sesión de administrador. */
    public static Sesion administrador() {
        return new Sesion(ModoUsuario.ADMINISTRADOR, ADMIN);
    }

    /** Sesión de un usuario normal. */
    public static Sesion usuario(String nombre) {
        return new Sesion(ModoUsuario.USUARIO, nombre);
    }

    public boolean esAdministrador() {
        return modo == ModoUsuario.ADMINISTRADOR;
    }
}

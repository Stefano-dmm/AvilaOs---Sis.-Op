package avilaos.archivos;

/** Modo en que se usa el sistema: el administrador puede todo y el usuario tiene acceso restringido. */
public enum ModoUsuario {
    /** Todas las operaciones sobre archivos, procesos, políticas, disco y métricas. */
    ADMINISTRADOR,
    /** Solo leer archivos propios o públicos y operar sobre los propios. */
    USUARIO
}

package avilaos.archivos;

/** Permiso de lectura de un archivo o directorio: quién, además de su dueño, puede leerlo. */
public enum Permiso {
    /** Solo su dueño (y el administrador) puede leerlo. */
    PRIVADO,
    /** Cualquier usuario puede leerlo. */
    PUBLICO
}

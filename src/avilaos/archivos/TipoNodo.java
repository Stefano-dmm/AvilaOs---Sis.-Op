package avilaos.archivos;

/** Qué es un elemento del sistema de archivos. */
public enum TipoNodo {
    /** Un archivo: tiene bloques en el disco. */
    ARCHIVO,
    /** Un directorio: contiene archivos y otros directorios, no ocupa bloques. */
    DIRECTORIO
}

package avilaos.disco;

/** Tipo de operación de E/S que un proceso pide al disco: las cuatro del CRUD de archivos y las dos de la suspensión. */
public enum OperacionES {
    /** Crea un archivo o directorio (asigna sus bloques). */
    CREAR,
    /** Lee un archivo (recorre su cadena de bloques). */
    LEER,
    /** Actualiza un archivo o directorio: cambia su nombre. */
    ACTUALIZAR,
    /** Elimina un archivo o directorio y libera sus bloques. */
    ELIMINAR,
    /** Escribe un proceso en el área de intercambio (swap out). */
    SUSPENDER,
    /** Trae un proceso del área de intercambio a la RAM (swap in). */
    REACTIVAR
}

package avilaos.nucleo.programas;

/**
 * Pasos del ciclo de un productor o consumidor. Cada paso, salvo el bloqueo por red, es una
 * instruccion de un ciclo. En el productor "primero" es vacios y "segundo" es llenos; en el
 * consumidor es al reves.
 */
public enum PasoComunicacion {
    TRABAJAR,
    WAIT_PRIMERO,
    WAIT_MUTEX,
    ACCESO_BUFFER,
    SIGNAL_MUTEX,
    SIGNAL_SEGUNDO
}

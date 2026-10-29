package avilaos.modelo;

/** Clase de proceso; decide que ProgramaProceso lo ejecuta. */
public enum TipoProceso {
    /** Muy consumidor del procesador: solo ejecuta instrucciones de CPU. */
    CPU_BOUND,
    /** Muy consumidor de E/S: despues de cada rafaga de CPU se bloquea esperando una E/S. */
    IO_BOUND,
    /** Produce elementos en un buffer acotado. */
    PRODUCTOR,
    /** Consume elementos de un buffer acotado. */
    CONSUMIDOR
}

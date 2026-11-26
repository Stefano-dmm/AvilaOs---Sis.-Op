package avilaos.disco;

/** Políticas de planificación del disco que se pueden elegir en la interfaz. */
public enum PoliticaDisco {
    /** Primero en llegar, primero en ser atendido. */
    FIFO,
    /** Shortest Seek Time First: atiende la solicitud más cercana al cabezal. */
    SSTF,
    /** El cabezal barre hacia un extremo atendiendo lo que encuentra y luego regresa. */
    SCAN,
    /** Como SCAN, pero solo atiende en una dirección: al llegar al extremo salta al otro lado sin atender. */
    C_SCAN
}

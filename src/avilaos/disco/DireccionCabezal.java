package avilaos.disco;

/** Dirección en que se mueve el cabezal del disco. */
public enum DireccionCabezal {
    /** Hacia los bloques de número mayor. */
    SUBE,
    /** Hacia los bloques de número menor. */
    BAJA;

    /** La dirección contraria. */
    public DireccionCabezal invertida() {
        return this == SUBE ? BAJA : SUBE;
    }
}

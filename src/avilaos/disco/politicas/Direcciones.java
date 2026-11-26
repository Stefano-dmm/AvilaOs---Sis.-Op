package avilaos.disco.politicas;

import avilaos.disco.DireccionCabezal;

/** Utilidad de las políticas de disco: decide la dirección en que queda el cabezal después de un viaje directo. */
final class Direcciones {

    /** Clase de utilidades: no se instancia. */
    private Direcciones() {
    }

    /** SUBE si el destino está más arriba, BAJA si está más abajo y la dirección actual si no se mueve. */
    static DireccionCabezal haciaBloque(int desde, int hasta, DireccionCabezal actual) {
        if (hasta > desde) {
            return DireccionCabezal.SUBE;
        }
        if (hasta < desde) {
            return DireccionCabezal.BAJA;
        }
        return actual;
    }
}

package avilaos.disco.politicas;

import avilaos.contratos.PoliticaDiscoStrategy;
import avilaos.disco.PoliticaDisco;

/** Crea la implementación de cada política de disco a partir de su nombre. Es el único lugar que hay que tocar para registrar una política nueva. */
public final class FabricaPoliticasDisco {

    /** Clase de utilidades: no se instancia. */
    private FabricaPoliticasDisco() {
    }

    /** Devuelve la política que corresponde al nombre dado. */
    public static PoliticaDiscoStrategy crear(PoliticaDisco politica) {
        return switch (politica) {
            case FIFO -> new FIFO();
            case SSTF -> new SSTF();
            case SCAN -> new SCAN();
            case C_SCAN -> new CSCAN();
        };
    }
}

package avilaos.nucleo.politicas;

import avilaos.contratos.PoliticaPlanificacionStrategy;
import avilaos.modelo.PoliticaPlanificacion;

public final class FabricaPoliticas {

    private FabricaPoliticas() {
    }

    public static PoliticaPlanificacionStrategy crear(PoliticaPlanificacion politica) {
        return switch (politica) {
            case FCFS -> new FCFS();
            case EDF -> new EDF();
            case ROUND_ROBIN -> new RoundRobin();
            case PRIORIDADES -> new PrioridadesApropiativas();
        };
    }
}

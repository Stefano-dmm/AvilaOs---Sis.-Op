package avilaos.nucleo.politicas;

import avilaos.contratos.PoliticaPlanificacionStrategy;
import avilaos.modelo.PoliticaPlanificacion;

/** Crea la implementacion de cada politica a partir de su nombre (el enum que elige el usuario). Es el unico lugar que hay que tocar para registrar una politica nueva. */
public final class FabricaPoliticas {

    /** Clase de utilidades: no se instancia. */
    private FabricaPoliticas() {
    }

    /** Devuelve una politica nueva (sin estado compartido) para el nombre dado. */
    public static PoliticaPlanificacionStrategy crear(PoliticaPlanificacion politica) {
        return switch (politica) {
            case FCFS -> new FCFS();
            case EDF -> new EDF();
            case ROUND_ROBIN -> new RoundRobin();
            case PRIORIDADES -> new PrioridadesApropiativas();
        };
    }
}

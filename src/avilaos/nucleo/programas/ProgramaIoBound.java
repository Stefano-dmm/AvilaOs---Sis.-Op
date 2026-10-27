package avilaos.nucleo.programas;

import avilaos.contratos.ProgramaProceso;
import avilaos.modelo.ModoCPU;
import avilaos.modelo.Proceso;
import avilaos.modelo.ResultadoCiclo;
import avilaos.nucleo.Computador;

/**
 * Proceso muy consumidor de E/S: despues de cada rafaga de CPU pide una operacion de entrada/salida
 * y se bloquea mientras dura, dejando la CPU libre para otro proceso.
 */
public class ProgramaIoBound implements ProgramaProceso {

    /** Instrucciones de CPU que ejecuta antes de pedir cada E/S. */
    private final int ciclosEntreES;
    /** Ciclos que dura cada E/S, durante los cuales esta bloqueado. */
    private final int ciclosES;
    /** Instrucciones de CPU ejecutadas en la rafaga actual. */
    private int cpuEnRafaga;

    /**
     * @param ciclosEntreES ciclos de CPU que ejecuta antes de cada solicitud de E/S
     * @param ciclosES ciclos que dura cada operacion de E/S
     */
    public ProgramaIoBound(int ciclosEntreES, int ciclosES) {
        if (ciclosEntreES < 1 || ciclosES < 1) {
            throw new IllegalArgumentException("Los ciclos entre E/S y de E/S deben ser al menos 1.");
        }
        this.ciclosEntreES = ciclosEntreES;
        this.ciclosES = ciclosES;
    }

    @Override
    public ResultadoCiclo ejecutar(Computador computador, Proceso proceso) {
        computador.setModoCPU(ModoCPU.USUARIO);
        proceso.ejecutarUnCiclo();
        if (proceso.estaTerminado()) {
            return ResultadoCiclo.TERMINADO;
        }
        cpuEnRafaga++;
        if (cpuEnRafaga >= ciclosEntreES) {
            cpuEnRafaga = 0;
            computador.bloquearPorTiempo(proceso, ciclosES, "E/S de " + ciclosES + " ciclos");
            return ResultadoCiclo.BLOQUEADO;
        }
        return ResultadoCiclo.CONTINUA;
    }
}

package avilaos.nucleo.politicas;

import avilaos.contratos.PoliticaPlanificacionStrategy;
import avilaos.estructuras.Cola;
import avilaos.modelo.Proceso;

/**
 * Round Robin: por turnos. La cola es FIFO y el Computador desaloja al proceso cuando agota su quantum
 * (configurable en ejecucion), asi que esta clase solo ordena; el control del quantum esta en Computador.
 */
public class RoundRobin implements PoliticaPlanificacionStrategy {

    /** Elige el primero de la cola. */
    @Override
    public Proceso seleccionar(Cola<Proceso> listos) {
        if (listos == null || listos.estaVacia()) {
            return null;
        }
        return listos.desencolar();
    }

    /** Pone el proceso al final de la cola (tambien al que fue desalojado por quantum). */
    @Override
    public void alLlegar(Proceso proceso, Cola<Proceso> listos) {
        if (proceso == null || listos == null) {
            return;
        }
        listos.encolar(proceso);
    }
}

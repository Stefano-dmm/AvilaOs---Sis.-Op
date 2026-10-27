package avilaos.nucleo.politicas;

import avilaos.contratos.PoliticaPlanificacionStrategy;
import avilaos.estructuras.Cola;
import avilaos.modelo.Proceso;

/** First Come First Served: los procesos se atienden en el orden en que llegan a Listos. No es apropiativa; es la mas simple pero no mira urgencia ni prioridad. */
public class FCFS implements PoliticaPlanificacionStrategy {

    /** Elige el primero de la cola. */
    @Override
    public Proceso seleccionar(Cola<Proceso> listos) {
        if (listos == null || listos.estaVacia()) {
            return null;
        }
        return listos.desencolar();
    }

    /** Pone el proceso al final de la cola. */
    @Override
    public void alLlegar(Proceso proceso, Cola<Proceso> listos) {
        if (proceso == null || listos == null) {
            return;
        }
        listos.encolar(proceso);
    }
}

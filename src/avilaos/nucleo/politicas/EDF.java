package avilaos.nucleo.politicas;

import avilaos.contratos.PoliticaPlanificacionStrategy;
import avilaos.estructuras.Cola;
import avilaos.estructuras.Nodo;
import avilaos.modelo.Proceso;

/**
 * Earliest Deadline First: va primero el proceso al que le queda menos para su deadline. La cola se mantiene
 * ordenada por deadline restante (que baja igual para todos, asi que el orden relativo no cambia). No
 * desaloja al proceso en ejecucion cuando llega uno con deadline mas cercano.
 */
public class EDF implements PoliticaPlanificacionStrategy {

    /** Busca el proceso con menor deadline restante y lo saca de la cola. */
    @Override
    public Proceso seleccionar(Cola<Proceso> listos) {
        if (listos == null || listos.estaVacia()) {
            return null;
        }

        Proceso elegido = null;
        Nodo<Proceso> actual = listos.getLista().getCabeza();
        while (actual != null) {
            Proceso candidato = actual.getDato();
            if (elegido == null || candidato.getDeadline() < elegido.getDeadline()) {
                elegido = candidato;
            }
            actual = actual.getSiguiente();
        }

        listos.getLista().eliminar(elegido);
        return elegido;
    }

    /** Inserta el proceso en su lugar segun el deadline: despues de los mas urgentes y antes del primero menos urgente (a igual deadline queda detras). */
    @Override
    public void alLlegar(Proceso proceso, Cola<Proceso> listos) {
        if (proceso == null || listos == null) {
            return;
        }

        Cola<Proceso> temporal = new Cola<>();
        boolean insertado = false;

        while (!listos.estaVacia()) {
            Proceso actual = listos.desencolar();
            if (!insertado && proceso.getDeadline() < actual.getDeadline()) {
                temporal.encolar(proceso);
                insertado = true;
            }
            temporal.encolar(actual);
        }

        if (!insertado) {
            temporal.encolar(proceso);
        }

        while (!temporal.estaVacia()) {
            listos.encolar(temporal.desencolar());
        }
    }
}

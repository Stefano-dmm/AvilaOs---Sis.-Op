package avilaos.nucleo.politicas;

import avilaos.contratos.PoliticaPlanificacionStrategy;
import avilaos.estructuras.Cola;
import avilaos.estructuras.Nodo;
import avilaos.modelo.Proceso;

/**
 * Prioridades apropiativas: va primero el proceso de mayor prioridad (numero mas alto) y, si llega o despierta
 * uno de mayor prioridad que el de la CPU, el Computador se la quita de inmediato. A igual prioridad se
 * respeta el orden de llegada. Los de baja prioridad pueden tardar mucho (inanicion).
 */
public class PrioridadesApropiativas implements PoliticaPlanificacionStrategy {

    /** Busca el proceso de mayor prioridad y lo saca de la cola. */
    @Override
    public Proceso seleccionar(Cola<Proceso> listos) {
        if (listos == null || listos.estaVacia()) {
            return null;
        }

        Proceso elegido = null;
        Nodo<Proceso> actual = listos.getLista().getCabeza();
        while (actual != null) {
            Proceso candidato = actual.getDato();
            if (elegido == null || candidato.getPrioridad() > elegido.getPrioridad()) {
                elegido = candidato;
            }
            actual = actual.getSiguiente();
        }

        listos.getLista().eliminar(elegido);
        return elegido;
    }

    /** Inserta el proceso en su lugar segun la prioridad (despues de los de igual o mayor prioridad). */
    @Override
    public void alLlegar(Proceso proceso, Cola<Proceso> listos) {
        if (proceso == null || listos == null) {
            return;
        }

        Cola<Proceso> temporal = new Cola<>();
        boolean insertado = false;

        while (!listos.estaVacia()) {
            Proceso actual = listos.desencolar();
            if (!insertado && proceso.getPrioridad() > actual.getPrioridad()) {
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

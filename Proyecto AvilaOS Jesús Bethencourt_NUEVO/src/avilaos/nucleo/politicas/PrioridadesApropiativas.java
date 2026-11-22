package avilaos.nucleo.politicas;

import avilaos.contratos.PoliticaPlanificacionStrategy;
import avilaos.estructuras.Cola;
import avilaos.estructuras.Nodo;
import avilaos.modelo.Proceso;

public class PrioridadesApropiativas implements PoliticaPlanificacionStrategy {

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

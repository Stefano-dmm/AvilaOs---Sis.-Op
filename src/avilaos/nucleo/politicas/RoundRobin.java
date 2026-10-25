package avilaos.nucleo.politicas;

import avilaos.contratos.PoliticaPlanificacionStrategy;
import avilaos.estructuras.Cola;
import avilaos.modelo.Proceso;

public class RoundRobin implements PoliticaPlanificacionStrategy {

    @Override
    public Proceso seleccionar(Cola<Proceso> listos) {
        if (listos == null || listos.estaVacia()) {
            return null;
        }
        return listos.desencolar();
    }

    @Override
    public void alLlegar(Proceso proceso, Cola<Proceso> listos) {
        if (proceso == null || listos == null) {
            return;
        }
        listos.encolar(proceso);
    }
}

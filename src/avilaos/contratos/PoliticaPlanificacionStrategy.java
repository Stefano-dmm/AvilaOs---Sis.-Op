package avilaos.contratos;

import avilaos.estructuras.Cola;
import avilaos.modelo.Proceso;

public interface PoliticaPlanificacionStrategy {

    Proceso seleccionar(Cola<Proceso> listos);

    void alLlegar(Proceso proceso, Cola<Proceso> listos);
}

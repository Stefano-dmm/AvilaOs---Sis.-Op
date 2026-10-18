package avilaos.contratos;

import avilaos.estructuras.Cola;
import avilaos.modelo.Proceso;

/**
 * Contrato de las políticas de corto plazo.
 * Agregar una política nueva implica implementar esta interfaz.
 */
public interface PoliticaPlanificacionStrategy {

    /**
     * Elige el siguiente proceso a ejecutar desde la cola de listos.
     * Debe extraerlo de la cola si lo selecciona.
     *
     * @return el proceso elegido, o null si la cola está vacía
     */
    Proceso seleccionar(Cola<Proceso> listos);

    /**
     * Inserta un proceso en la cola de listos según el criterio de la política.
     */
    void alLlegar(Proceso proceso, Cola<Proceso> listos);
}

package avilaos.contratos;

import avilaos.estructuras.Cola;
import avilaos.modelo.Proceso;

/**
 * Politica de planificacion de un computador (patron Strategy). Decide como se ordena la cola de listos y que
 * proceso pasa a la CPU. Agregar una politica nueva no obliga a modificar el planificador (Computador): se
 * crea otra clase que la implemente y se registra en FabricaPoliticas. Cada computador tiene la suya y se
 * puede cambiar en ejecucion.
 */
public interface PoliticaPlanificacionStrategy {

    /** Saca de la cola de listos y devuelve el proceso que debe ejecutar a continuacion; null si la cola esta vacia. */
    Proceso seleccionar(Cola<Proceso> listos);

    /** Inserta en la cola de listos un proceso que llega (nuevo, despertado o desalojado), en la posicion que corresponda a la politica. */
    void alLlegar(Proceso proceso, Cola<Proceso> listos);
}

package avilaos.config;

import avilaos.modelo.TipoProceso;

/**
 * Atributos originales de un proceso.
 *
 * @param indiceComputador posicion (desde 0) del computador; -1 deja que AvilaOS lo asigne por balanceo
 * @param indiceBuffer posicion (desde 0) del buffer de un productor o consumidor; -1 si no aplica
 * @param intervalo CPU bound: sin uso. I/O bound: ciclos de CPU entre cada E/S. Productor y consumidor:
 *                  ciclos que tarda en producir o consumir un elemento
 * @param elementos productor y consumidor: elementos que debe producir o consumir para terminar
 * @param ciclosES I/O bound: ciclos que dura cada E/S
 * @param instrucciones CPU bound e I/O bound: ciclos de CPU que necesita; en productor y consumidor se
 *                      calcula a partir de los elementos y el intervalo
 */
public record ConfigProceso(String nombre, TipoProceso tipo, int prioridad, int memoria, int deadline,
                            int instrucciones, int indiceComputador, int indiceBuffer, int intervalo,
                            int elementos, int ciclosES) {

    /** Proceso sin buffer ni E/S, como un CPU bound. */
    public ConfigProceso(String nombre, TipoProceso tipo, int prioridad, int memoria, int deadline,
                         int instrucciones, int indiceComputador) {
        this(nombre, tipo, prioridad, memoria, deadline, instrucciones, indiceComputador, -1, 1, 0, 0);
    }
}

package avilaos.config;

import avilaos.modelo.TipoProceso;

/** Atributos originales de un proceso; indiceComputador es la posicion (desde 0) del computador. */
public record ConfigProceso(String nombre, TipoProceso tipo, int prioridad, int memoria,
                            int deadline, int instrucciones, int indiceComputador) {
}

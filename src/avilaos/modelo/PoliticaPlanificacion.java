package avilaos.modelo;

/** Nombre de la politica de planificacion de un computador (lo que se elige en la interfaz); FabricaPoliticas crea su implementacion. */
public enum PoliticaPlanificacion {
    /** Primero en llegar, primero en ser atendido. */
    FCFS,
    /** Primero el deadline mas cercano (Earliest Deadline First). */
    EDF,
    /** Por turnos con quantum configurable. */
    ROUND_ROBIN,
    /** Mayor prioridad primero, apropiativa. */
    PRIORIDADES
}

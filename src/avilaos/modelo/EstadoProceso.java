package avilaos.modelo;

/** Estado de un proceso en su ciclo de vida (ver el diagrama de estados del informe). */
public enum EstadoProceso {
    /** Creado y esperando RAM en la cola de nuevos del computador. */
    NUEVO,
    /** Con RAM asignada y esperando la CPU en la cola de listos. */
    LISTO,
    /** Ocupa la CPU del computador. */
    EJECUCION,
    /** Espera un semaforo, una E/S o la red; siempre tiene un motivo (Proceso.motivoBloqueo). */
    BLOQUEADO,
    /** Completo sus instrucciones o llego a su deadline; ya liberó su RAM. */
    TERMINADO
}

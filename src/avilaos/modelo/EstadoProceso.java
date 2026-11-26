package avilaos.modelo;

/** Estado de un proceso en su ciclo de vida: el modelo de siete estados (ver el diagrama de estados del informe). */
public enum EstadoProceso {
    /** Creado y esperando RAM en la cola de nuevos del computador (cola a largo plazo). */
    NUEVO,
    /** Con RAM asignada y esperando la CPU en la cola de listos. */
    LISTO,
    /** Ocupa la CPU del computador. */
    EJECUCION,
    /** Espera un semáforo, una E/S o la red; siempre tiene un motivo (Proceso.motivoBloqueo). */
    BLOQUEADO,
    /** Listo para ejecutar pero sacado de la RAM: su memoria está en el área de intercambio del disco. */
    LISTO_SUSPENDIDO,
    /** Bloqueado y sacado de la RAM; cuando ocurre su evento pasa a LISTO_SUSPENDIDO. */
    BLOQUEADO_SUSPENDIDO,
    /** Completó sus instrucciones o llegó a su deadline; ya liberó su RAM. */
    TERMINADO
}

package avilaos.modelo;

/** Si la CPU ejecuta un programa de usuario o trabajo del sistema operativo (semWait, semSignal, insertar, extraer). */
public enum ModoCPU {
    /** Ejecutando una instruccion de un programa de usuario. */
    USUARIO,
    /** Ejecutando una operacion del nucleo, o la CPU esta libre. */
    SISTEMA_OPERATIVO
}

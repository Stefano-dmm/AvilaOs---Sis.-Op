package avilaos.contratos;

import avilaos.modelo.Proceso;
import avilaos.modelo.ResultadoCiclo;
import avilaos.nucleo.Computador;

/**
 * El "codigo" que ejecuta un proceso, una instruccion por ciclo. Cada tipo de proceso (CPU bound,
 * I/O bound, productor, consumidor) tiene su propio programa, y el computador no necesita saber
 * cual es: agregar un tipo nuevo no obliga a modificar el nucleo.
 */
public interface ProgramaProceso {

    /** Ejecuta la instruccion de este ciclo y dice si el proceso sigue, se bloqueo o termino. */
    ResultadoCiclo ejecutar(Computador computador, Proceso proceso);

    /** Se llama cuando el proceso termina (por completarse o por deadline) para soltar lo que tuviera. */
    default void alTerminar(Computador computador, Proceso proceso) {
    }
}

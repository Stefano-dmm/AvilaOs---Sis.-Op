package avilaos.nucleo;

import avilaos.contratos.CriterioSuspension;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.modelo.Proceso;

/**
 * Criterio de suspensión: se suspende al proceso Bloqueado de menor prioridad y, si hay empate, al que lleva más ciclos
 * bloqueado. Razón: un proceso bloqueado no usa la CPU y el de menor prioridad es el que menos perjudica al sistema si
 * tarda más en volver; entre iguales, el que lleva más tiempo esperando su evento es el que más probablemente seguirá bloqueado.
 */
public class MenorPrioridadMasBloqueado implements CriterioSuspension {

    /** Recorre los candidatos y se queda con el de menor prioridad (con empate, el de más ciclos bloqueado, y luego el primero). */
    @Override
    public Proceso elegir(ListaEnlazada<Proceso> candidatos, int cicloActual) {
        Proceso elegido = null;
        Nodo<Proceso> actual = candidatos.getCabeza();
        while (actual != null) {
            Proceso p = actual.getDato();
            if (elegido == null || esMejorVictima(p, elegido, cicloActual)) {
                elegido = p;
            }
            actual = actual.getSiguiente();
        }
        return elegido;
    }

    private static boolean esMejorVictima(Proceso a, Proceso b, int cicloActual) {
        if (a.getPrioridad() != b.getPrioridad()) {
            return a.getPrioridad() < b.getPrioridad();
        }
        return (cicloActual - a.getCicloBloqueo()) > (cicloActual - b.getCicloBloqueo());
    }
}

package avilaos.contratos;

import avilaos.estructuras.ListaEnlazada;
import avilaos.modelo.Proceso;

/**
 * Criterio con el que el planificador a mediano plazo elige a qué proceso Bloqueado suspender cuando falta RAM. Es una
 * interfaz porque es una decisión de diseño intercambiable: otro criterio (el que más memoria libera, el de menor
 * deadline...) se agrega sin tocar el planificador.
 */
public interface CriterioSuspension {

    /**
     * Elige uno de los candidatos (procesos Bloqueados con memoria en la RAM y que no se están suspendiendo ya).
     *
     * @param candidatos procesos que se pueden suspender; nunca es null
     * @param cicloActual ciclo en curso, para calcular cuánto lleva bloqueado cada uno
     * @return el proceso elegido, o null si no hay candidatos
     */
    Proceso elegir(ListaEnlazada<Proceso> candidatos, int cicloActual);
}

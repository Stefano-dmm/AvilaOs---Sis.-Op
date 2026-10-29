package avilaos.contratos;

import avilaos.estructuras.ListaEnlazada;
import avilaos.modelo.Proceso;
import avilaos.nucleo.Computador;

/**
 * Criterio con el que AvilaOS elige el computador de un proceso cuando el usuario no lo indica
 * (balanceo de carga). Es una interfaz para poder cambiar el criterio sin tocar el cluster.
 */
public interface CriterioAsignacion {

    /** Devuelve el computador elegido; lanza IllegalArgumentException si ninguno puede recibir el proceso. */
    Computador elegir(ListaEnlazada<Computador> computadores, Proceso proceso);
}

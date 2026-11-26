package avilaos.contratos;

import avilaos.disco.DireccionCabezal;
import avilaos.disco.PlanMovimiento;
import avilaos.disco.SolicitudES;
import avilaos.estructuras.Cola;

/**
 * Política de planificación del disco. El Disco le entrega la cola de solicitudes y el estado del cabezal, y la
 * política devuelve cuál atender y por dónde viaja el cabezal. Agregar una política nueva es implementar esta
 * interfaz y registrarla en la fábrica: el Disco no cambia.
 */
public interface PoliticaDiscoStrategy {

    /**
     * Elige la solicitud a atender; devuelve null si la cola está vacía. No modifica la cola: el Disco la saca.
     *
     * @param cola solicitudes esperando, en orden de llegada
     * @param posicion bloque donde está el cabezal
     * @param direccion dirección en que se mueve el cabezal
     * @param ultimoBloque último bloque al que llega el barrido (extremo alto para SCAN y C-SCAN)
     */
    PlanMovimiento seleccionar(Cola<SolicitudES> cola, int posicion, DireccionCabezal direccion, int ultimoBloque);
}

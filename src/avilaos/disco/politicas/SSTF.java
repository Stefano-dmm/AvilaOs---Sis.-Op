package avilaos.disco.politicas;

import avilaos.contratos.PoliticaDiscoStrategy;
import avilaos.disco.DireccionCabezal;
import avilaos.disco.PlanMovimiento;
import avilaos.disco.SolicitudES;
import avilaos.estructuras.Cola;
import avilaos.estructuras.Nodo;

/** Shortest Seek Time First: atiende la solicitud más cercana al cabezal; con empate, la que llegó antes. Reduce el desplazamiento pero puede dejar esperando a las lejanas. */
public class SSTF implements PoliticaDiscoStrategy {

    /** Recorre la cola y se queda con la solicitud de menor distancia al cabezal. */
    @Override
    public PlanMovimiento seleccionar(Cola<SolicitudES> cola, int posicion, DireccionCabezal direccion, int ultimoBloque) {
        SolicitudES elegida = null;
        int menor = Integer.MAX_VALUE;
        Nodo<SolicitudES> actual = cola.getLista().getCabeza();
        while (actual != null) {
            int distancia = Math.abs(actual.getDato().getBloque() - posicion);
            if (distancia < menor) {
                menor = distancia;
                elegida = actual.getDato();
            }
            actual = actual.getSiguiente();
        }
        if (elegida == null) {
            return null;
        }
        return PlanMovimiento.directo(elegida, Direcciones.haciaBloque(posicion, elegida.getBloque(), direccion));
    }
}

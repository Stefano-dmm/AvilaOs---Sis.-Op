package avilaos.disco.politicas;

import avilaos.contratos.PoliticaDiscoStrategy;
import avilaos.disco.DireccionCabezal;
import avilaos.disco.PlanMovimiento;
import avilaos.disco.SolicitudES;
import avilaos.estructuras.Cola;
import avilaos.estructuras.Nodo;

/**
 * C-SCAN (SCAN circular): el cabezal solo atiende hacia los bloques mayores. Al llegar al extremo alto salta al bloque
 * 0 sin atender nada (el salto no cuenta como desplazamiento) y vuelve a subir, así todas las solicitudes esperan parecido.
 */
public class CSCAN implements PoliticaDiscoStrategy {

    /** Elige la solicitud más cercana hacia arriba; si no hay, viaja al extremo, salta al 0 y elige la de menor bloque. */
    @Override
    public PlanMovimiento seleccionar(Cola<SolicitudES> cola, int posicion, DireccionCabezal direccion, int ultimoBloque) {
        if (cola.estaVacia()) {
            return null;
        }
        SolicitudES adelante = SCAN.masCercanaEnDireccion(cola, posicion, DireccionCabezal.SUBE);
        if (adelante != null) {
            return PlanMovimiento.directo(adelante, DireccionCabezal.SUBE);
        }
        return PlanMovimiento.porExtremo(menorBloque(cola), ultimoBloque, 0, DireccionCabezal.SUBE);
    }

    /** La solicitud de menor número de bloque (la primera que llegó si hay empate). */
    private static SolicitudES menorBloque(Cola<SolicitudES> cola) {
        SolicitudES elegida = null;
        Nodo<SolicitudES> actual = cola.getLista().getCabeza();
        while (actual != null) {
            if (elegida == null || actual.getDato().getBloque() < elegida.getBloque()) {
                elegida = actual.getDato();
            }
            actual = actual.getSiguiente();
        }
        return elegida;
    }
}

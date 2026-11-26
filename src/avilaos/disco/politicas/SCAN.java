package avilaos.disco.politicas;

import avilaos.contratos.PoliticaDiscoStrategy;
import avilaos.disco.DireccionCabezal;
import avilaos.disco.PlanMovimiento;
import avilaos.disco.SolicitudES;
import avilaos.estructuras.Cola;
import avilaos.estructuras.Nodo;

/**
 * SCAN (algoritmo del ascensor): el cabezal avanza en su dirección atendiendo las solicitudes que encuentra; cuando
 * ya no hay más adelante llega hasta el extremo del disco, invierte la dirección y vuelve atendiendo las demás.
 */
public class SCAN implements PoliticaDiscoStrategy {

    /** Elige la solicitud más cercana en la dirección actual; si no hay, viaja al extremo, invierte y elige la más cercana al extremo. */
    @Override
    public PlanMovimiento seleccionar(Cola<SolicitudES> cola, int posicion, DireccionCabezal direccion, int ultimoBloque) {
        if (cola.estaVacia()) {
            return null;
        }
        SolicitudES adelante = masCercanaEnDireccion(cola, posicion, direccion);
        if (adelante != null) {
            return PlanMovimiento.directo(adelante, direccion);
        }
        DireccionCabezal contraria = direccion.invertida();
        int extremo = (direccion == DireccionCabezal.SUBE) ? ultimoBloque : 0;
        SolicitudES atras = masCercanaEnDireccion(cola, extremo, contraria);
        if (atras == null) {
            // solo pasa si hay una solicitud más allá del extremo recibido: se atiende en orden de llegada
            return PlanMovimiento.directo(cola.frente(), contraria);
        }
        return PlanMovimiento.porExtremo(atras, extremo, -1, contraria);
    }

    /** La solicitud más cercana a la posición yendo hacia arriba (bloque mayor o igual) o hacia abajo (menor o igual); null si no hay. */
    static SolicitudES masCercanaEnDireccion(Cola<SolicitudES> cola, int posicion, DireccionCabezal direccion) {
        SolicitudES elegida = null;
        Nodo<SolicitudES> actual = cola.getLista().getCabeza();
        while (actual != null) {
            SolicitudES s = actual.getDato();
            boolean enDireccion = (direccion == DireccionCabezal.SUBE) ? s.getBloque() >= posicion : s.getBloque() <= posicion;
            if (enDireccion && (elegida == null || estaMasCerca(s, elegida, posicion))) {
                elegida = s;
            }
            actual = actual.getSiguiente();
        }
        return elegida;
    }

    /** Verdadero si el bloque de a está estrictamente más cerca de la posición que el de b (el empate se queda con la que llegó antes). */
    private static boolean estaMasCerca(SolicitudES a, SolicitudES b, int posicion) {
        return Math.abs(a.getBloque() - posicion) < Math.abs(b.getBloque() - posicion);
    }
}

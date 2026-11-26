package avilaos.disco.politicas;

import avilaos.contratos.PoliticaDiscoStrategy;
import avilaos.disco.DireccionCabezal;
import avilaos.disco.PlanMovimiento;
import avilaos.disco.SolicitudES;
import avilaos.estructuras.Cola;

/** First In First Out: atiende las solicitudes en el orden en que llegaron, sin mirar dónde está el cabezal. Es justa pero hace viajes largos. */
public class FIFO implements PoliticaDiscoStrategy {

    /** Elige la primera solicitud de la cola. */
    @Override
    public PlanMovimiento seleccionar(Cola<SolicitudES> cola, int posicion, DireccionCabezal direccion, int ultimoBloque) {
        SolicitudES primera = cola.frente();
        if (primera == null) {
            return null;
        }
        return PlanMovimiento.directo(primera, Direcciones.haciaBloque(posicion, primera.getBloque(), direccion));
    }
}

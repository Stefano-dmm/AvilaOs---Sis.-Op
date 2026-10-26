package avilaos.nucleo.balanceo;

import avilaos.contratos.CriterioAsignacion;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.modelo.Proceso;
import avilaos.nucleo.Computador;

/**
 * Balanceo por menor carga (join-the-shortest-queue): el proceso va al computador con menos
 * procesos vivos. Es el criterio clasico para repartir trabajo cuando no se conoce su duracion
 * y no hay migracion: mantiene parejas las colas y evita que un computador se sature mientras otro
 * queda libre.
 *
 * <p>Reglas, en orden: (1) se descartan los computadores cuya RAM total nunca podria admitir al
 * proceso; (2) gana el de menor carga; (3) a igual carga, el de mas RAM libre; (4) a igual RAM
 * libre, el de menor id (asi la eleccion es predecible).
 */
public class MenorCarga implements CriterioAsignacion {

    @Override
    public Computador elegir(ListaEnlazada<Computador> computadores, Proceso proceso) {
        Computador mejor = null;
        Nodo<Computador> actual = computadores.getCabeza();
        while (actual != null) {
            Computador candidato = actual.getDato();
            if (candidato.getRamTotal() >= proceso.getMemoriaRequerida() && esMejor(candidato, mejor)) {
                mejor = candidato;
            }
            actual = actual.getSiguiente();
        }
        if (mejor == null) {
            throw new IllegalArgumentException("Ningun computador tiene RAM suficiente para "
                    + proceso.getEtiqueta() + " (" + proceso.getMemoriaRequerida() + ").");
        }
        return mejor;
    }

    private static boolean esMejor(Computador candidato, Computador actualMejor) {
        if (actualMejor == null) {
            return true;
        }
        if (candidato.getCargaActual() != actualMejor.getCargaActual()) {
            return candidato.getCargaActual() < actualMejor.getCargaActual();
        }
        if (candidato.getRamLibre() != actualMejor.getRamLibre()) {
            return candidato.getRamLibre() > actualMejor.getRamLibre();
        }
        return candidato.getId() < actualMejor.getId();
    }
}

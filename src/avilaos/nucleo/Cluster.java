package avilaos.nucleo;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.modelo.Proceso;

public class Cluster {

    private final ListaEnlazada<Computador> computadores = new ListaEnlazada<>();

    public void agregarComputador(Computador computador) {
        if (computador == null) {
            throw new IllegalArgumentException("El computador no puede ser null.");
        }
        computadores.agregarAlFinal(computador);
    }

    public int getCantidadComputadores() {
        return computadores.getTamanio();
    }

    public ListaEnlazada<Computador> getComputadores() {
        return computadores;
    }

    public Computador obtenerPorId(int computadorId) {
        Nodo<Computador> actual = computadores.getCabeza();
        while (actual != null) {
            if (actual.getDato().getId() == computadorId) {
                return actual.getDato();
            }
            actual = actual.getSiguiente();
        }
        return null;
    }

    public Computador obtenerPorIndice(int indice) {
        return computadores.obtener(indice);
    }

    public void asignarProcesoManual(Proceso proceso, int computadorId) {
        if (proceso == null) {
            throw new IllegalArgumentException("El proceso no puede ser null.");
        }
        if (proceso.getComputadorId() != -1) {
            throw new IllegalStateException(
                    "El proceso " + proceso.getEtiqueta() + " ya fue asignado al PC "
                            + proceso.getComputadorId() + " (sin migracion).");
        }

        Computador destino = obtenerPorId(computadorId);
        if (destino == null) {
            throw new IllegalArgumentException("No existe computador con id " + computadorId);
        }

        destino.recibirProceso(proceso);
    }

    public void onTickGlobal(int cicloGlobal) {
        Nodo<Computador> actual = computadores.getCabeza();
        while (actual != null) {
            actual.getDato().onTick(cicloGlobal);
            actual = actual.getSiguiente();
        }
    }

    @Override
    public String toString() {
        return "Cluster " + computadores.toString();
    }
}

package avilaos.nucleo;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.RelojGlobal;
import avilaos.modelo.Proceso;
import avilaos.sincronizacion.BufferSO;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

public class Cluster {

    public static final int MAX_CAPACIDAD_BUFFER = 1000;
    public static final int MAX_LATENCIA_RED = 1000;

    private final ListaEnlazada<Computador> computadores = new ListaEnlazada<>();
    private final ListaEnlazada<BufferSO> buffers = new ListaEnlazada<>();
    private final Semaphore cerrojo = new Semaphore(1);
    private volatile Consumer<String> oyenteEventos;
    private int latenciaRed;
    private int contadorBuffers;

    public void agregarComputador(Computador computador) {
        if (computador == null) {
            throw new IllegalArgumentException("El computador no puede ser null.");
        }
        computador.setCluster(this);
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

    public int getLatenciaRed() {
        return latenciaRed;
    }

    /** Ciclos extra que cuesta cada acceso a un buffer de otro computador. */
    public void setLatenciaRed(int latenciaRed) {
        if (latenciaRed < 0 || latenciaRed > MAX_LATENCIA_RED) {
            throw new IllegalArgumentException(
                    "La latencia de red debe estar entre 0 y " + MAX_LATENCIA_RED + ".");
        }
        this.latenciaRed = latenciaRed;
    }

    public ListaEnlazada<BufferSO> getBuffers() {
        return buffers;
    }

    public BufferSO obtenerBuffer(int indice) {
        return buffers.obtener(indice);
    }

    /** Crea un buffer en la RAM del computador anfitrion; falla si no hay memoria suficiente. */
    public BufferSO crearBuffer(String nombre, int capacidad, int computadorId) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del buffer no puede estar vacio.");
        }
        if (nombre.contains(",")) {
            throw new IllegalArgumentException("El nombre del buffer no puede contener comas.");
        }
        if (capacidad < 1 || capacidad > MAX_CAPACIDAD_BUFFER) {
            throw new IllegalArgumentException(
                    "La capacidad del buffer debe estar entre 1 y " + MAX_CAPACIDAD_BUFFER + ".");
        }
        Computador anfitrion = obtenerPorId(computadorId);
        if (anfitrion == null) {
            throw new IllegalArgumentException("No existe computador con id " + computadorId);
        }
        BufferSO buffer = new BufferSO(contadorBuffers + 1, nombre, capacidad, anfitrion);
        anfitrion.reservarMemoria(buffer.getMemoria());
        contadorBuffers++;
        buffers.agregarAlFinal(buffer);
        registrarEvento("Se crea el buffer " + buffer.getNombre() + " (capacidad " + capacidad
                + ") en " + anfitrion.getNombre());
        return buffer;
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

    /** Destino de los mensajes del log de eventos (por ejemplo la ventana). Puede ser null. */
    public void setOyenteEventos(Consumer<String> oyente) {
        this.oyenteEventos = oyente;
    }

    public void registrarEvento(String mensaje) {
        Consumer<String> oyente = oyenteEventos;
        if (oyente != null) {
            oyente.accept(mensaje);
        }
    }

    /**
     * Crea el reloj global con todos los computadores suscritos: en cada tic cada uno avanza un ciclo,
     * cada uno en su propio hilo. El reloj toma el cerrojo del cluster mientras dura cada ciclo.
     */
    public RelojGlobal crearReloj(long velocidadMs) {
        RelojGlobal reloj = new RelojGlobal(velocidadMs);
        reloj.setCerrojo(cerrojo);
        Nodo<Computador> actual = computadores.getCabeza();
        while (actual != null) {
            reloj.suscribir(actual.getDato());
            actual = actual.getSiguiente();
        }
        return reloj;
    }

    /** Cerrojo del estado compartido: lo toma el reloj en cada ciclo y la interfaz al modificar. */
    public void adquirir() {
        cerrojo.acquireUninterruptibly();
    }

    public void liberar() {
        cerrojo.release();
    }

    @Override
    public String toString() {
        return "Cluster " + computadores.toString();
    }
}

package avilaos.nucleo;

import avilaos.contratos.CriterioAsignacion;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.RelojGlobal;
import avilaos.modelo.Proceso;
import avilaos.nucleo.balanceo.MenorCarga;
import avilaos.sincronizacion.BufferSO;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * El cluster: el conjunto de computadores y de buffers, mas lo que es comun a todos (latencia de red, criterio de
 * balanceo, log de eventos y el cerrojo del estado compartido). Es quien crea el reloj global y asigna los
 * procesos a los computadores. La interfaz grafica solo lo observa y lo modifica a traves de sus metodos.
 *
 * <p><b>Concurrencia.</b> El Cluster no tiene hilos propios; el que protege su estado es cerrojo: el reloj lo
 * mantiene tomado durante cada ciclo y la interfaz lo toma antes de leer o cambiar algo, asi nunca se
 * mezclan. Las listas computadores y buffers solo cambian desde la interfaz (con el cerrojo tomado) o al
 * construir el cluster.
 */
public class Cluster {

    /** Capacidad maxima de un buffer (huecos). */
    public static final int MAX_CAPACIDAD_BUFFER = 1000;
    /** Latencia de red maxima configurable, en ciclos. */
    public static final int MAX_LATENCIA_RED = 1000;

    /** Los computadores del cluster, en el orden en que se agregaron (el indice 0 es el primero). */
    private final ListaEnlazada<Computador> computadores = new ListaEnlazada<>();
    /** Los buffers del productor-consumidor, en el orden en que se crearon. */
    private final ListaEnlazada<BufferSO> buffers = new ListaEnlazada<>();
    /**
     * CERROJO DEL ESTADO COMPARTIDO. Semaphore de Java con un permiso: el RelojGlobal lo mantiene tomado durante cada
     * ciclo (los hilos de los computadores corren dentro de ese ciclo) y la interfaz lo toma antes de leer o
     * modificar cualquier cosa. Asi la interfaz nunca ve ni cambia un ciclo a medias. No es reentrante: quien
     * lo tiene tomado no debe volver a tomarlo.
     */
    private final Semaphore cerrojo = new Semaphore(1);
    /** Quien recibe los mensajes del log (la ventana); volatile porque lo cambia la interfaz y lo leen los hilos de los computadores. */
    private volatile Consumer<String> oyenteEventos;
    /** Ciclos extra que cuesta cada acceso a un buffer que vive en otro computador. */
    private int latenciaRed;
    /** Cantidad de buffers creados, para darles numeros de creacion. */
    private int contadorBuffers;
    /** Criterio de balanceo de carga para la asignacion automatica (intercambiable); por defecto, menor carga. */
    private CriterioAsignacion criterio = new MenorCarga();

    /** Agrega un computador al cluster y le dice a cual pertenece. */
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

    /** Busca un computador por su id; null si no existe. */
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

    /** Devuelve el computador en la posicion dada (desde 0), o null. */
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

    /** Devuelve el buffer en la posicion dada (desde 0), o null. */
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
        Nodo<BufferSO> existente = buffers.getCabeza();
        while (existente != null) {
            if (existente.getDato().getNombre().equalsIgnoreCase(nombre.trim())) {
                throw new IllegalArgumentException("Ya existe un buffer llamado " + nombre.trim() + ".");
            }
            existente = existente.getSiguiente();
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

    /** Entrega el proceso al computador elegido por el usuario. Un proceso ya asignado no se reasigna (no hay migracion). */
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

    /** AvilaOS elige el computador con el criterio de balanceo y le entrega el proceso. */
    public Computador asignarProcesoAutomatico(Proceso proceso) {
        if (proceso == null) {
            throw new IllegalArgumentException("El proceso no puede ser null.");
        }
        if (proceso.getComputadorId() != -1) {
            throw new IllegalStateException(
                    "El proceso " + proceso.getEtiqueta() + " ya fue asignado al PC "
                            + proceso.getComputadorId() + " (sin migracion).");
        }
        Computador destino = criterio.elegir(computadores, proceso);
        registrarEvento("AvilaOS asigna " + proceso.getEtiqueta() + " a " + destino.getNombre()
                + " (" + destino.getCargaActual() + " procesos activos antes de la llegada)");
        destino.recibirProceso(proceso);
        return destino;
    }

    public CriterioAsignacion getCriterio() {
        return criterio;
    }

    public void setCriterio(CriterioAsignacion criterio) {
        if (criterio == null) {
            throw new IllegalArgumentException("El criterio de asignacion no puede ser null.");
        }
        this.criterio = criterio;
    }

    /**
     * Avanza un ciclo a TODOS los computadores uno detras de otro, sin hilos. Es la version determinista que usan las
     * pruebas y los experimentos; en la aplicacion los avanza el RelojGlobal con un hilo por computador.
     */
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

    /** Envia un mensaje al log de eventos si hay alguien escuchando. Lo pueden llamar los hilos de todos los computadores. */
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

    /** Suelta el cerrojo del estado compartido. */
    public void liberar() {
        cerrojo.release();
    }

    @Override
    public String toString() {
        return "Cluster " + computadores.toString();
    }
}

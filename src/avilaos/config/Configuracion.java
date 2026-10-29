package avilaos.config;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.sincronizacion.BufferSO;

/** Parametros de una simulacion: lo que se guarda y se carga desde archivo. */
public class Configuracion {

    /** Limites de validacion de los parametros: la interfaz y los archivos de configuracion los comparten. */
    public static final int MIN_COMPUTADORES = 2;
    public static final int MAX_COMPUTADORES = 8;
    public static final int MIN_CICLO_MS = 50;
    public static final int MAX_CICLO_MS = 10000;
    public static final int MIN_LATENCIA = 0;
    public static final int MAX_LATENCIA = 1000;
    public static final int MIN_RAM = 1;
    public static final int MAX_RAM = 1000000;
    public static final int MIN_QUANTUM = 1;
    public static final int MAX_QUANTUM = 1000;
    public static final int MAX_CAPACIDAD_BUFFER = 1000;
    public static final int MAX_INSTRUCCIONES = 100000;
    public static final int MAX_INTERVALO = 1000;
    public static final int MAX_ELEMENTOS = 10000;

    /** Duracion de un ciclo de reloj, en milisegundos. */
    private int cicloMs = 500;
    /** Ciclos extra por acceso a un buffer remoto. */
    private int latenciaRed = 2;
    /** Computadores configurados, en orden; la posicion es el indice que usan buffers y procesos. */
    private final ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
    /** Buffers configurados, en orden; la posicion es el indice que usan los procesos productores y consumidores. */
    private final ListaEnlazada<ConfigBuffer> buffers = new ListaEnlazada<>();
    /** Procesos iniciales (los que se guardan en el archivo para repetir la simulacion). */
    private final ListaEnlazada<ConfigProceso> procesos = new ListaEnlazada<>();

    /** Configuracion inicial: dos computadores, uno con FCFS y otro con Round Robin, sin procesos ni buffers. */
    public static Configuracion porDefecto() {
        Configuracion c = new Configuracion();
        c.agregarComputador(new ConfigComputador("Nodo-A", 512, PoliticaPlanificacion.FCFS, 3));
        c.agregarComputador(new ConfigComputador("Nodo-B", 512, PoliticaPlanificacion.ROUND_ROBIN, 3));
        return c;
    }

    public int getCicloMs() {
        return cicloMs;
    }

    public void setCicloMs(int cicloMs) {
        validarRango("Duracion del ciclo", cicloMs, MIN_CICLO_MS, MAX_CICLO_MS);
        this.cicloMs = cicloMs;
    }

    public int getLatenciaRed() {
        return latenciaRed;
    }

    public void setLatenciaRed(int latenciaRed) {
        validarRango("Latencia de red", latenciaRed, MIN_LATENCIA, MAX_LATENCIA);
        this.latenciaRed = latenciaRed;
    }

    public ListaEnlazada<ConfigComputador> getComputadores() {
        return computadores;
    }

    public ListaEnlazada<ConfigBuffer> getBuffers() {
        return buffers;
    }

    public ListaEnlazada<ConfigProceso> getProcesos() {
        return procesos;
    }

    /** Agrega un computador validando nombre, RAM, quantum y politica. */
    public void agregarComputador(ConfigComputador pc) {
        if (pc.nombre() == null || pc.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del computador no puede estar vacio.");
        }
        if (pc.nombre().contains(",")) {
            throw new IllegalArgumentException("El nombre del computador no puede contener comas.");
        }
        validarRango("RAM de " + pc.nombre(), pc.ram(), MIN_RAM, MAX_RAM);
        validarRango("Quantum de " + pc.nombre(), pc.quantum(), MIN_QUANTUM, MAX_QUANTUM);
        if (pc.politica() == null) {
            throw new IllegalArgumentException("Falta la politica de " + pc.nombre());
        }
        computadores.agregarAlFinal(pc);
    }

    /** El buffer ocupa RAM en su anfitrion, asi que todos los buffers de un computador deben caber en el. */
    public void agregarBuffer(ConfigBuffer b) {
        if (b.nombre() == null || b.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del buffer no puede estar vacio.");
        }
        if (b.nombre().contains(",")) {
            throw new IllegalArgumentException("El nombre del buffer no puede contener comas.");
        }
        ConfigComputador anfitrion = computadores.obtener(b.indiceComputador());
        if (anfitrion == null) {
            throw new IllegalArgumentException("El buffer " + b.nombre() + " apunta a un computador que no existe.");
        }
        Nodo<ConfigBuffer> existente = buffers.getCabeza();
        while (existente != null) {
            if (existente.getDato().nombre().equalsIgnoreCase(b.nombre().trim())) {
                throw new IllegalArgumentException("Ya existe un buffer llamado " + b.nombre().trim() + ".");
            }
            existente = existente.getSiguiente();
        }
        validarRango("Capacidad del buffer " + b.nombre(), b.capacidad(), 1, MAX_CAPACIDAD_BUFFER);
        int memoria = memoriaDeBuffersEn(b.indiceComputador()) + b.capacidad() * BufferSO.TAMANO_ELEMENTO;
        if (memoria > anfitrion.ram()) {
            throw new IllegalArgumentException("Los buffers de " + anfitrion.nombre()
                    + " no caben en su RAM (" + memoria + " > " + anfitrion.ram() + ").");
        }
        buffers.agregarAlFinal(b);
    }

    /** RAM que ya ocupan los buffers configurados en un computador. */
    private int memoriaDeBuffersEn(int indiceComputador) {
        int total = 0;
        Nodo<ConfigBuffer> actual = buffers.getCabeza();
        while (actual != null) {
            if (actual.getDato().indiceComputador() == indiceComputador) {
                total += actual.getDato().capacidad() * BufferSO.TAMANO_ELEMENTO;
            }
            actual = actual.getSiguiente();
        }
        return total;
    }

    /**
     * Agrega un proceso inicial validando nombre, tipo, memoria (contra la RAM de su computador), prioridad, deadline y los campos
     * propios de su tipo (E/S, buffer, ciclos y elementos).
     */
    public void agregarProceso(ConfigProceso p) {
        if (p.nombre() == null || p.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del proceso no puede estar vacio.");
        }
        if (p.nombre().contains(",")) {
            throw new IllegalArgumentException("El nombre del proceso no puede contener comas.");
        }
        if (p.tipo() == null) {
            throw new IllegalArgumentException("Falta el tipo del proceso " + p.nombre() + ".");
        }
        int ramMaxima = ramDisponibleParaProceso(p);
        validarRango("Memoria de " + p.nombre(), p.memoria(), 1, ramMaxima);
        validarRango("Prioridad de " + p.nombre(), p.prioridad(), 1, 100);
        validarRango("Deadline de " + p.nombre(), p.deadline(), 1, 1000000);

        switch (p.tipo()) {
            case CPU_BOUND ->
                validarRango("Instrucciones de " + p.nombre(), p.instrucciones(), 1, MAX_INSTRUCCIONES);
            case IO_BOUND -> {
                validarRango("Instrucciones de " + p.nombre(), p.instrucciones(), 1, MAX_INSTRUCCIONES);
                validarRango("Ciclos entre E/S de " + p.nombre(), p.intervalo(), 1, MAX_INTERVALO);
                validarRango("Ciclos de E/S de " + p.nombre(), p.ciclosES(), 1, MAX_INTERVALO);
            }
            case PRODUCTOR, CONSUMIDOR -> {
                if (buffers.obtener(p.indiceBuffer()) == null) {
                    throw new IllegalArgumentException("El proceso " + p.nombre() + " apunta a un buffer que no existe.");
                }
                validarRango("Ciclos por elemento de " + p.nombre(), p.intervalo(), 1, MAX_INTERVALO);
                validarRango("Elementos de " + p.nombre(), p.elementos(), 1, MAX_ELEMENTOS);
            }
        }
        procesos.agregarAlFinal(p);
    }

    /** RAM maxima que puede pedir el proceso: la de su computador, o la mayor del cluster si es automatico. */
    private int ramDisponibleParaProceso(ConfigProceso p) {
        if (p.indiceComputador() == -1) {
            int mayor = 0;
            Nodo<ConfigComputador> actual = computadores.getCabeza();
            while (actual != null) {
                mayor = Math.max(mayor, actual.getDato().ram());
                actual = actual.getSiguiente();
            }
            return Math.max(mayor, 1);
        }
        ConfigComputador destino = computadores.obtener(p.indiceComputador());
        if (destino == null) {
            throw new IllegalArgumentException("El proceso " + p.nombre()
                    + " apunta a un computador que no existe.");
        }
        return destino.ram();
    }

    /** Comprueba las reglas de toda la configuracion (cantidad de computadores). */
    public void validarCompleta() {
        if (computadores.getTamanio() < MIN_COMPUTADORES) {
            throw new IllegalArgumentException("Se necesitan al menos " + MIN_COMPUTADORES + " computadores.");
        }
        if (computadores.getTamanio() > MAX_COMPUTADORES) {
            throw new IllegalArgumentException("Se permiten como maximo " + MAX_COMPUTADORES + " computadores.");
        }
    }

    /** Lanza IllegalArgumentException con un mensaje claro si el valor esta fuera del rango. */
    public static void validarRango(String campo, int valor, int min, int max) {
        if (valor < min || valor > max) {
            throw new IllegalArgumentException(campo + " debe estar entre " + min + " y " + max + ".");
        }
    }
}

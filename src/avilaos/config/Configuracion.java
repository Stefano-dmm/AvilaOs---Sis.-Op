package avilaos.config;

import avilaos.estructuras.ListaEnlazada;
import avilaos.modelo.PoliticaPlanificacion;

/** Parametros de una simulacion: lo que se guarda y se carga desde archivo. */
public class Configuracion {

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

    private int cicloMs = 500;
    private int latenciaRed = 2;
    private final ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
    private final ListaEnlazada<ConfigProceso> procesos = new ListaEnlazada<>();

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

    public ListaEnlazada<ConfigProceso> getProcesos() {
        return procesos;
    }

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

    public void agregarProceso(ConfigProceso p) {
        if (p.nombre() == null || p.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del proceso no puede estar vacio.");
        }
        if (p.nombre().contains(",")) {
            throw new IllegalArgumentException("El nombre del proceso no puede contener comas.");
        }
        ConfigComputador destino = computadores.obtener(p.indiceComputador());
        if (destino == null) {
            throw new IllegalArgumentException("El proceso " + p.nombre()
                    + " apunta a un computador que no existe.");
        }
        validarRango("Instrucciones de " + p.nombre(), p.instrucciones(), 1, 100000);
        validarRango("Memoria de " + p.nombre(), p.memoria(), 1, destino.ram());
        validarRango("Prioridad de " + p.nombre(), p.prioridad(), 1, 100);
        validarRango("Deadline de " + p.nombre(), p.deadline(), 1, 1000000);
        procesos.agregarAlFinal(p);
    }

    public void validarCompleta() {
        if (computadores.getTamanio() < MIN_COMPUTADORES) {
            throw new IllegalArgumentException("Se necesitan al menos " + MIN_COMPUTADORES + " computadores.");
        }
        if (computadores.getTamanio() > MAX_COMPUTADORES) {
            throw new IllegalArgumentException("Se permiten como maximo " + MAX_COMPUTADORES + " computadores.");
        }
    }

    public static void validarRango(String campo, int valor, int min, int max) {
        if (valor < min || valor > max) {
            throw new IllegalArgumentException(campo + " debe estar entre " + min + " y " + max + ".");
        }
    }
}

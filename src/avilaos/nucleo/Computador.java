package avilaos.nucleo;

import avilaos.contratos.Ciclico;
import avilaos.contratos.PoliticaPlanificacionStrategy;
import avilaos.estructuras.Cola;
import avilaos.estructuras.Nodo;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.ModoCPU;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.sincronizacion.SemaforoSO;
import java.util.concurrent.Semaphore;

public class Computador implements Ciclico {

    private static int contadorIds = 0;

    private final int id;
    private String nombre;
    private int ramTotal;
    private int ramUsada;
    private ModoCPU modoCPU;
    private int quantum;
    private int quantumRestante;
    private Proceso procesoEnCPU;
    private PoliticaPlanificacionStrategy politica;
    private PoliticaPlanificacion nombrePolitica;
    private int cicloActual;
    private int ciclosTotales;
    private int ciclosOcupados;

    private final Cola<Proceso> colaNuevos = new Cola<>();
    private final Cola<Proceso> colaListos = new Cola<>();
    private final Cola<Proceso> colaBloqueados = new Cola<>();
    private final Cola<Proceso> colaTerminados = new Cola<>();

    private Cluster cluster;
    private final Cola<Proceso> despertares = new Cola<>();
    private final Semaphore candadoDespertares = new Semaphore(1);

    public Computador(String nombre, int ramTotal, int quantum) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del computador no puede estar vacío.");
        }
        if (ramTotal <= 0) {
            throw new IllegalArgumentException("La RAM debe ser mayor que 0.");
        }
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor que 0.");
        }

        this.id = ++contadorIds;
        this.nombre = nombre.trim();
        this.ramTotal = ramTotal;
        this.ramUsada = 0;
        this.modoCPU = ModoCPU.SISTEMA_OPERATIVO;
        this.quantum = quantum;
        this.quantumRestante = quantum;
        this.procesoEnCPU = null;
        this.politica = null;
        this.nombrePolitica = null;
    }

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getCicloActual() {
        return cicloActual;
    }

    /** Ciclos de reloj que este computador ha vivido. */
    public int getCiclosTotales() {
        return ciclosTotales;
    }

    /** Ciclos en que la CPU ejecuto una instruccion de un proceso de usuario. */
    public int getCiclosOcupados() {
        return ciclosOcupados;
    }

    public int getRamTotal() {
        return ramTotal;
    }

    public void setRamTotal(int ramTotal) {
        this.ramTotal = ramTotal;
    }

    public int getRamUsada() {
        return ramUsada;
    }

    public int getRamLibre() {
        return ramTotal - ramUsada;
    }

    public ModoCPU getModoCPU() {
        return modoCPU;
    }

    public void setModoCPU(ModoCPU modoCPU) {
        this.modoCPU = modoCPU;
    }

    public int getQuantum() {
        return quantum;
    }

    public void setQuantum(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor que 0.");
        }
        this.quantum = quantum;
        this.quantumRestante = quantum;
    }

    public int getQuantumRestante() {
        return quantumRestante;
    }

    public void setQuantumRestante(int quantumRestante) {
        this.quantumRestante = quantumRestante;
    }

    public Proceso getProcesoEnCPU() {
        return procesoEnCPU;
    }

    public void setProcesoEnCPU(Proceso procesoEnCPU) {
        this.procesoEnCPU = procesoEnCPU;
    }

    public PoliticaPlanificacionStrategy getPolitica() {
        return politica;
    }

    public PoliticaPlanificacion getNombrePolitica() {
        return nombrePolitica;
    }

    public void setPolitica(PoliticaPlanificacionStrategy politica, PoliticaPlanificacion nombre) {
        this.politica = politica;
        this.nombrePolitica = nombre;
    }

    public Cola<Proceso> getColaNuevos() {
        return colaNuevos;
    }

    public Cola<Proceso> getColaListos() {
        return colaListos;
    }

    public Cola<Proceso> getColaBloqueados() {
        return colaBloqueados;
    }

    public Cola<Proceso> getColaTerminados() {
        return colaTerminados;
    }

    public void reservarMemoria(int cantidad) {
        if (cantidad < 0 || cantidad > getRamLibre()) {
            throw new IllegalStateException("No hay memoria suficiente en " + nombre);
        }
        ramUsada += cantidad;
    }

    public void liberarMemoria(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("Cantidad de memoria inválida.");
        }
        ramUsada -= cantidad;
        if (ramUsada < 0) {
            ramUsada = 0;
        }
    }

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    /** Avisa de una decision importante al log de eventos del cluster (si hay uno conectado). */
    public void registrarEvento(String mensaje) {
        if (cluster != null) {
            cluster.registrarEvento(mensaje);
        }
    }

    private void pasarABloqueados(Proceso proceso, String motivo, int ciclos) {
        if (procesoEnCPU == proceso) {
            procesoEnCPU = null;
            modoCPU = ModoCPU.SISTEMA_OPERATIVO;
        }
        proceso.setEstado(EstadoProceso.BLOQUEADO);
        proceso.setMotivoBloqueo(motivo);
        proceso.setBloqueoRestante(ciclos);
        colaBloqueados.encolar(proceso);
    }

    /** El proceso ya quedo en la cola del semaforo: lo saca de la CPU y lo pasa a Bloqueado. */
    public void bloquearPorSemaforo(Proceso proceso, String motivo) {
        pasarABloqueados(proceso, motivo, -1);
        registrarEvento(proceso.getEtiqueta() + " se bloquea en el " + motivo);
    }

    /** Bloqueo que termina solo despues de unos ciclos (E/S o latencia de red). */
    public void bloquearPorTiempo(Proceso proceso, int ciclos, String motivo) {
        // +1 porque el contador baja al comienzo de cada ciclo siguiente
        pasarABloqueados(proceso, motivo, ciclos + 1);
        registrarEvento(proceso.getEtiqueta() + " se bloquea: " + motivo);
    }

    /**
     * semSignal hecho por un proceso de este computador. El proceso despertado puede vivir en otro
     * computador (otro hilo), asi que se le avisa por su bandeja de despertares en vez de tocar sus colas.
     */
    public void liberarSemaforo(SemaforoSO semaforo) {
        Proceso despertado = semaforo.semSignal();
        if (despertado == null) {
            return;
        }
        Computador destino = (cluster == null) ? null : cluster.obtenerPorId(despertado.getComputadorId());
        (destino == null ? this : destino).recibirDespertar(despertado);
    }

    public void recibirDespertar(Proceso proceso) {
        candadoDespertares.acquireUninterruptibly();
        try {
            despertares.encolar(proceso);
        } finally {
            candadoDespertares.release();
        }
    }

    /** Pasa a Listos a los procesos que otro computador desperto. Se llama al comienzo de cada ciclo. */
    public void procesarDespertares() {
        while (true) {
            Proceso proceso;
            candadoDespertares.acquireUninterruptibly();
            try {
                proceso = despertares.desencolar();
            } finally {
                candadoDespertares.release();
            }
            if (proceso == null) {
                return;
            }
            if (proceso.getEstado() == EstadoProceso.BLOQUEADO && colaBloqueados.getLista().eliminar(proceso)) {
                desbloquear(proceso);
                registrarEvento(proceso.getEtiqueta() + " despierta y pasa a Listos en " + nombre);
            }
        }
    }

    /** Baja un ciclo a los bloqueos por tiempo y cuenta el tiempo bloqueado en semaforos. */
    public void avanzarBloqueados() {
        int pendientes = colaBloqueados.getTamanio();
        for (int i = 0; i < pendientes; i++) {
            Proceso proceso = colaBloqueados.desencolar();
            if (proceso.getBloqueoRestante() > 0) {
                proceso.setBloqueoRestante(proceso.getBloqueoRestante() - 1);
                if (proceso.getBloqueoRestante() == 0) {
                    desbloquear(proceso);
                    continue;
                }
            } else if (proceso.getSemaforoBloqueo() != null) {
                proceso.sumarCicloBloqueadoSemaforo();
            }
            colaBloqueados.encolar(proceso);
        }
    }

    private void desbloquear(Proceso proceso) {
        proceso.setMotivoBloqueo(null);
        proceso.setBloqueoRestante(-1);
        proceso.setEstado(EstadoProceso.LISTO);
        pasarAListos(proceso);
    }

    public void recibirProceso(Proceso proceso) {
        if (proceso == null) {
            throw new IllegalArgumentException("El proceso no puede ser null.");
        }
        proceso.setComputadorId(id);
        proceso.setEstado(EstadoProceso.NUEVO);
        proceso.setCicloLlegada(cicloActual);
        colaNuevos.encolar(proceso);
        intentarAdmitirNuevos();
    }

    public int intentarAdmitirNuevos() {
        int admitidos = 0;
        int pendientes = colaNuevos.getTamanio();

        for (int i = 0; i < pendientes; i++) {
            Proceso proceso = colaNuevos.desencolar();
            if (proceso == null) {
                break;
            }

            if (proceso.getMemoriaRequerida() <= getRamLibre()) {
                reservarMemoria(proceso.getMemoriaRequerida());
                proceso.setEstado(EstadoProceso.LISTO);
                pasarAListos(proceso);
                admitidos++;
            } else {
                colaNuevos.encolar(proceso);
            }
        }

        return admitidos;
    }

    public void terminarProceso(Proceso proceso) {
        if (proceso == null) {
            return;
        }

        if (procesoEnCPU == proceso) {
            procesoEnCPU = null;
            modoCPU = ModoCPU.SISTEMA_OPERATIVO;
        }

        colaListos.getLista().eliminar(proceso);
        colaBloqueados.getLista().eliminar(proceso);
        colaNuevos.getLista().eliminar(proceso);

        if (proceso.getEstado() != EstadoProceso.NUEVO) {
            liberarMemoria(proceso.getMemoriaRequerida());
        }

        proceso.setEstado(EstadoProceso.TERMINADO);
        proceso.setCicloFin(cicloActual);
        if (!colaTerminados.getLista().contiene(proceso)) {
            colaTerminados.encolar(proceso);
        }

        intentarAdmitirNuevos();
    }

    private void pasarAListos(Proceso proceso) {
        if (politica != null) {
            politica.alLlegar(proceso, colaListos);
        } else {
            colaListos.encolar(proceso);
        }
    }

    public static void reiniciarContadorIds() {
        contadorIds = 0;
    }

    @Override
    public void onTick(int cicloGlobal) {
        cicloActual = cicloGlobal;
        ciclosTotales++;
        if (procesoEnCPU == null) {
            despacharSiguiente();
        } else if (nombrePolitica == PoliticaPlanificacion.PRIORIDADES
                && hayListoConMayorPrioridad()) {
            desalojarProcesoActual();
            despacharSiguiente();
        }

        if (procesoEnCPU == null) {
            modoCPU = ModoCPU.SISTEMA_OPERATIVO;
            return;
        }

        modoCPU = ModoCPU.USUARIO;
        ciclosOcupados++;
        procesoEnCPU.ejecutarUnCiclo();
        boolean porDeadline = procesoEnCPU.decrementarDeadline();

        if (porDeadline || procesoEnCPU.estaTerminado()) {
            terminarProceso(procesoEnCPU);
            despacharSiguiente();
            return;
        }

        if (nombrePolitica == PoliticaPlanificacion.ROUND_ROBIN) {
            quantumRestante--;
            if (quantumRestante <= 0) {
                if (!colaListos.estaVacia()) {
                    desalojarProcesoActual();
                    despacharSiguiente();
                } else {
                    quantumRestante = quantum;
                }
            }
        }
    }

    private void desalojarProcesoActual() {
        if (procesoEnCPU == null || politica == null) {
            return;
        }
        procesoEnCPU.setEstado(EstadoProceso.LISTO);
        politica.alLlegar(procesoEnCPU, colaListos);
        procesoEnCPU = null;
        modoCPU = ModoCPU.SISTEMA_OPERATIVO;
    }

    private boolean hayListoConMayorPrioridad() {
        if (procesoEnCPU == null) {
            return false;
        }
        Nodo<Proceso> actual = colaListos.getLista().getCabeza();
        while (actual != null) {
            if (actual.getDato().getPrioridad() > procesoEnCPU.getPrioridad()) {
                return true;
            }
            actual = actual.getSiguiente();
        }
        return false;
    }

    private void despacharSiguiente() {
        if (politica == null) {
            return;
        }
        Proceso siguiente = politica.seleccionar(colaListos);
        if (siguiente == null) {
            procesoEnCPU = null;
            modoCPU = ModoCPU.SISTEMA_OPERATIVO;
            return;
        }
        siguiente.setEstado(EstadoProceso.EJECUCION);
        if (siguiente.getCicloPrimerDespacho() < 0) {
            siguiente.setCicloPrimerDespacho(cicloActual);
        }
        procesoEnCPU = siguiente;
        quantumRestante = quantum;
        modoCPU = ModoCPU.USUARIO;
    }

    @Override
    public String toString() {
        String cpu = (procesoEnCPU == null) ? "idle" : procesoEnCPU.getEtiqueta();
        String pol = (nombrePolitica == null) ? "sin-politica" : nombrePolitica.toString();
        return "PC" + id + "-" + nombre
                + " [RAM " + ramUsada + "/" + ramTotal
                + ", CPU=" + cpu
                + ", modo=" + modoCPU
                + ", pol=" + pol
                + ", q=" + quantum
                + ", nuevos=" + colaNuevos.getTamanio()
                + ", listos=" + colaListos.getTamanio()
                + ", bloq=" + colaBloqueados.getTamanio()
                + ", term=" + colaTerminados.getTamanio() + "]";
    }
}

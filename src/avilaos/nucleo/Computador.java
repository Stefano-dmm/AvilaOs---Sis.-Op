package avilaos.nucleo;

import avilaos.contratos.PoliticaPlanificacionStrategy;
import avilaos.contratos.ProgramaProceso;
import avilaos.estructuras.Cola;
import avilaos.hardware.CPU;
import avilaos.hardware.Clockable;
import avilaos.hardware.RAM;
import avilaos.estructuras.Nodo;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.ModoCPU;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.modelo.ResultadoCiclo;
import avilaos.nucleo.programas.ProgramaCpuBound;
import avilaos.sincronizacion.SemaforoSO;
import java.util.concurrent.Semaphore;

/**
 * Un computador del cluster: la unidad reutilizable de AvilaOS. Reune hardware (CPU y RAM) y un nucleo de
 * sistema operativo con planificador y colas. Todos los computadores son instancias de esta misma clase;
 * agregar uno al cluster es crear otra instancia.
 *
 * <p><b>Modelo de hilos.</b> Cada Computador corre en su propio hilo, que el RelojGlobal despierta una vez
 * por ciclo (tic). Durante un tic ese hilo es el unico que toca las colas, la CPU, la RAM y los procesos de
 * este computador, y la interfaz no puede tocarlos porque el reloj mantiene tomado el cerrojo del Cluster.
 * Lo unico que otros hilos hacen sobre un Computador mientras corre es dejar avisos en su bandeja de
 * despertares (protegida por candadoDespertares) y operar los SemaforoSO y BufferSO, que tienen sus propios
 * candados.
 *
 * <p><b>Que protege a que:</b>
 * <ul>
 * <li>colaNuevos, colaListos, colaBloqueados, colaTerminados, cpu y ram: el hilo de este computador
 * (y la interfaz solo con el cerrojo del Cluster tomado, es decir, nunca a la vez que un tic);</li>
 * <li>despertares: candadoDespertares, porque la escriben otros computadores y la lee este;</li>
 * <li>cada SemaforoSO y cada BufferSO: su propio candado.</li>
 * </ul>
 */
public class Computador implements Clockable {

    /** Programa de los procesos que no traen uno propio: se comportan como CPU bound. Es sin estado, asi que se comparte. */
    private static final ProgramaProceso PROGRAMA_POR_DEFECTO = new ProgramaCpuBound();
    /** Contador para dar un id distinto a cada computador del sistema. */
    private static int contadorIds = 0;

    /** Id unico del computador; los procesos y buffers lo usan para referirse a el. */
    private final int id;
    /** Nombre mostrado en la interfaz y en el log. */
    private String nombre;
    /** Procesador de este computador: guarda el proceso en ejecucion, el modo y los registros PC y MAR. */
    private final CPU cpu = new CPU();
    /** Memoria principal: lleva la cuenta de la memoria usada y libre (procesos y buffers). */
    private final RAM ram;
    /** Quantum de Round Robin: ciclos que un proceso puede usar la CPU antes de ceder el turno. Se puede cambiar en ejecucion. */
    private int quantum;
    /** Ciclos que le quedan al proceso actual de su quantum. */
    private int quantumRestante;
    /** Estrategia que decide el orden de la cola de listos y que proceso va a la CPU (interfaz: se puede cambiar en ejecucion). */
    private PoliticaPlanificacionStrategy politica;
    /** Nombre de la politica en uso (lo que muestra el selector de la interfaz). */
    private PoliticaPlanificacion nombrePolitica;
    /** Ultimo ciclo global que vivio este computador; se usa para sellar llegadas, despachos y fines de los procesos. */
    private int cicloActual;
    /** Ciclos de reloj que este computador ha vivido (denominador de la utilizacion). */
    private int ciclosTotales;
    /** Ciclos en que la CPU ejecuto una instruccion de un proceso (numerador de la utilizacion). */
    private int ciclosOcupados;

    /** COLA DE NUEVOS: procesos que esperan RAM (planificacion de largo plazo). Sale de aqui cuando cabe en memoria. */
    private final Cola<Proceso> colaNuevos = new Cola<>();
    /** COLA DE LISTOS: procesos con RAM que esperan la CPU. Es unica por computador; la politica decide su orden. */
    private final Cola<Proceso> colaListos = new Cola<>();
    /** COLA DE BLOQUEADOS: procesos que esperan un semaforo, una E/S o la red. Cada uno tiene su motivo de bloqueo. */
    private final Cola<Proceso> colaBloqueados = new Cola<>();
    /** COLA DE TERMINADOS: procesos que acabaron (por completarse o por deadline); ya liberaron su RAM. */
    private final Cola<Proceso> colaTerminados = new Cola<>();

    /** Cluster al que pertenece (para buscar otros computadores, la latencia de red y el log de eventos). */
    private Cluster cluster;
    /**
     * BANDEJA DE DESPERTARES: procesos de este computador a los que otro proceso desperto con semSignal. La
     * escriben hilos de otros computadores y la vacia este al comienzo de su siguiente ciclo (como una
     * interrupcion). Protegida por candadoDespertares.
     */
    private final Cola<Proceso> despertares = new Cola<>();
    /**
     * Semaphore de Java que protege la bandeja despertares: la escribe el hilo de otro computador (recibirDespertar)
     * mientras el de este la lee (procesarDespertares). Es el unico punto por el que otro computador "entra" aqui.
     */
    private final Semaphore candadoDespertares = new Semaphore(1);

    /** Crea un computador con su RAM, su quantum y sin politica (hay que asignarla con setPolitica). Valida nombre, RAM y quantum. */
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
        this.ram = new RAM(ramTotal);
        this.quantum = quantum;
        this.quantumRestante = quantum;
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

    /** Procesos vivos de este computador: en CPU, listos, bloqueados y esperando memoria. */
    public int getCargaActual() {
        return (cpu.getProcesoActual() == null ? 0 : 1) + colaNuevos.getTamanio()
                + colaListos.getTamanio() + colaBloqueados.getTamanio();
    }

    public CPU getCpu() {
        return cpu;
    }

    public RAM getRam() {
        return ram;
    }

    public int getRamTotal() {
        return ram.getCapacidadTotal();
    }

    public void setRamTotal(int ramTotal) {
        ram.setCapacidadTotal(ramTotal);
    }

    public int getRamUsada() {
        return ram.getMemoriaUsada();
    }

    public int getRamLibre() {
        return ram.getMemoriaDisponible();
    }

    public ModoCPU getModoCPU() {
        return cpu.getModo();
    }

    public void setModoCPU(ModoCPU modoCPU) {
        cpu.setModo(modoCPU);
    }

    public int getQuantum() {
        return quantum;
    }

    /** Cambia el quantum en ejecucion (debe ser mayor que 0). */
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
        return cpu.getProcesoActual();
    }

    public void setProcesoEnCPU(Proceso proceso) {
        cpu.cargarProceso(proceso);
    }

    public PoliticaPlanificacionStrategy getPolitica() {
        return politica;
    }

    public PoliticaPlanificacion getNombrePolitica() {
        return nombrePolitica;
    }

    /** Cambia la politica en ejecucion, de forma independiente en este computador. Los procesos ya en la cola de listos se conservan. */
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

    /** Reserva RAM que no es de un proceso (por ejemplo la de un buffer). */
    public void reservarMemoria(int cantidad) {
        if (!ram.reservar(cantidad)) {
            throw new IllegalStateException("No hay memoria suficiente en " + nombre);
        }
    }

    /** Devuelve RAM reservada con reservarMemoria. */
    public void liberarMemoria(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("Cantidad de memoria inválida.");
        }
        ram.liberar(cantidad);
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

    /** Saca al proceso de la CPU si la ocupaba, lo marca Bloqueado con su motivo y lo deja en la cola de bloqueados. */
    private void pasarABloqueados(Proceso proceso, String motivo, int ciclos) {
        if (cpu.getProcesoActual() == proceso) {
            cpu.cargarProceso(null);
            cpu.setModo(ModoCPU.SISTEMA_OPERATIVO);
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

    /** Deja un aviso en la bandeja de despertares. Se puede llamar desde el hilo de otro computador. */
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

    /** Pasa un proceso de Bloqueado a Listo: borra su motivo y lo inserta en la cola de listos segun la politica. */
    private void desbloquear(Proceso proceso) {
        proceso.setMotivoBloqueo(null);
        proceso.setBloqueoRestante(-1);
        proceso.setEstado(EstadoProceso.LISTO);
        pasarAListos(proceso);
    }

    /** Entrega un proceso nuevo a este computador: queda en Nuevos con su ciclo de llegada y se intenta admitirlo. */
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

    /**
     * Planificacion de largo plazo: recorre una vez la cola de nuevos y pasa a Listos a los que caben en la RAM; los demas siguen
     * esperando. Devuelve cuantos admitio.
     */
    public int intentarAdmitirNuevos() {
        int admitidos = 0;
        int pendientes = colaNuevos.getTamanio();

        for (int i = 0; i < pendientes; i++) {
            Proceso proceso = colaNuevos.desencolar();
            if (proceso == null) {
                break;
            }

            if (ram.asignarMemoria(proceso)) {
                proceso.setEstado(EstadoProceso.LISTO);
                pasarAListos(proceso);
                admitidos++;
            } else {
                colaNuevos.encolar(proceso);
            }
        }

        return admitidos;
    }

    /**
     * Termina un proceso (por completarse o por deadline): le da la oportunidad de soltar lo que tuviera (alTerminar), lo saca de
     * la CPU y de las colas, libera su RAM, lo deja en Terminados y revisa si algun proceso de Nuevos ya cabe. Ignora a un proceso ya terminado.
     */
    public void terminarProceso(Proceso proceso) {
        if (proceso == null || colaTerminados.getLista().contiene(proceso)) {
            return;
        }

        if (proceso.getPrograma() != null) {
            proceso.getPrograma().alTerminar(this, proceso);
        }

        if (cpu.getProcesoActual() == proceso) {
            cpu.cargarProceso(null);
        }

        colaListos.getLista().eliminar(proceso);
        colaBloqueados.getLista().eliminar(proceso);
        colaNuevos.getLista().eliminar(proceso);

        // Solo libera si el proceso llego a ocupar RAM (los que esperaban en Nuevos no la tienen).
        ram.liberarMemoria(proceso);

        proceso.setEstado(EstadoProceso.TERMINADO);
        proceso.setCicloFin(cicloActual);
        if (!colaTerminados.getLista().contiene(proceso)) {
            colaTerminados.encolar(proceso);
        }

        intentarAdmitirNuevos();
    }

    /** Inserta el proceso en la cola de listos con el orden que decide la politica. */
    private void pasarAListos(Proceso proceso) {
        if (politica != null) {
            politica.alLlegar(proceso, colaListos);
        } else {
            colaListos.encolar(proceso);
        }
    }

    /** Reinicia el contador de ids (se usa al construir un cluster nuevo y en las pruebas). */
    public static void reiniciarContadorIds() {
        contadorIds = 0;
    }

    /** Lo llama el reloj global: el computador avanza exactamente un ciclo. */
    @Override
    public void tic(long ciclo) {
        onTick((int) ciclo);
    }

    /**
     * Un ciclo de reloj de este computador, siempre en este orden:
     * 1) procesa los despertares que dejaron otros computadores;
     * 2) avanza los bloqueos por tiempo (E/S y red);
     * 3) si la CPU esta libre despacha segun la politica (o desaloja por prioridad);
     * 4) ejecuta UNA instruccion del proceso en la CPU con su programa;
     * 5) baja el deadline de todos los procesos vivos y termina a los que llegan a 0.
     */
    public void onTick(int cicloGlobal) {
        cicloActual = cicloGlobal;
        ciclosTotales++;
        procesarDespertares();
        avanzarBloqueados();

        if (cpu.getProcesoActual() == null) {
            despacharSiguiente();
        } else if (nombrePolitica == PoliticaPlanificacion.PRIORIDADES
                && hayListoConMayorPrioridad()) {
            desalojarProcesoActual();
            despacharSiguiente();
        }

        if (cpu.getProcesoActual() == null) {
            cpu.setModo(ModoCPU.SISTEMA_OPERATIVO);
        } else {
            ejecutarProcesoActual();
        }
        vencerDeadlines();
    }

    /** Ejecuta la instruccion de este ciclo con el programa del proceso y reacciona a lo que ocurra. */
    private void ejecutarProcesoActual() {
        Proceso proceso = cpu.getProcesoActual();
        ciclosOcupados++;
        ProgramaProceso programa = (proceso.getPrograma() != null) ? proceso.getPrograma() : PROGRAMA_POR_DEFECTO;
        ResultadoCiclo resultado = programa.ejecutar(this, proceso);
        cpu.registrarPc(proceso.getPc());

        switch (resultado) {
            case TERMINADO -> {
                registrarEvento(proceso.getEtiqueta() + " completa sus instrucciones y termina en " + nombre);
                terminarProceso(proceso);
                despacharSiguiente();
            }
            case BLOQUEADO -> despacharSiguiente();
            case CONTINUA -> controlarQuantum();
        }
    }

    /** Round Robin: resta un ciclo al quantum del proceso actual y, si se agoto y hay otros listos, lo desaloja y despacha al siguiente. */
    private void controlarQuantum() {
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

    /**
     * El deadline baja un ciclo por cada ciclo de reloj para todo proceso vivo (en CPU, listo,
     * bloqueado o esperando memoria). Al llegar a 0 el proceso se termina.
     */
    private void vencerDeadlines() {
        Proceso[] vivos = procesosVivos();
        for (Proceso proceso : vivos) {
            if (proceso.getEstado() != EstadoProceso.TERMINADO && proceso.descontarDeadline()) {
                registrarEvento(proceso.getEtiqueta() + " llega a su deadline en " + nombre + " y es terminado");
                terminarProceso(proceso);
            }
        }
        if (cpu.getProcesoActual() == null) {
            despacharSiguiente();
        }
    }

    /** Foto (arreglo) de los procesos vivos: en CPU, nuevos, listos y bloqueados. Sirve para recorrerlos mientras se terminan procesos sin invalidar los recorridos. */
    private Proceso[] procesosVivos() {
        Proceso[] vivos = new Proceso[(cpu.getProcesoActual() == null ? 0 : 1) + colaNuevos.getTamanio()
                + colaListos.getTamanio() + colaBloqueados.getTamanio()];
        int i = 0;
        if (cpu.getProcesoActual() != null) {
            vivos[i++] = cpu.getProcesoActual();
        }
        i = copiar(colaNuevos, vivos, i);
        i = copiar(colaListos, vivos, i);
        copiar(colaBloqueados, vivos, i);
        return vivos;
    }

    /** Copia los procesos de una cola al arreglo a partir de la posicion dada y devuelve la siguiente posicion libre. */
    private static int copiar(Cola<Proceso> cola, Proceso[] destino, int desde) {
        int i = desde;
        Nodo<Proceso> actual = cola.getLista().getCabeza();
        while (actual != null) {
            destino[i++] = actual.getDato();
            actual = actual.getSiguiente();
        }
        return i;
    }

    /** Quita el proceso de la CPU y lo devuelve a Listos (por quantum vencido o prioridad mayor), conservando lo que ya ejecuto. */
    private void desalojarProcesoActual() {
        if (cpu.getProcesoActual() == null || politica == null) {
            return;
        }
        cpu.getProcesoActual().setEstado(EstadoProceso.LISTO);
        politica.alLlegar(cpu.getProcesoActual(), colaListos);
        cpu.cargarProceso(null);
        cpu.setModo(ModoCPU.SISTEMA_OPERATIVO);
    }

    /** Politica de prioridades apropiativas: verdadero si algun proceso listo tiene mayor prioridad que el de la CPU. */
    private boolean hayListoConMayorPrioridad() {
        if (cpu.getProcesoActual() == null) {
            return false;
        }
        Nodo<Proceso> actual = colaListos.getLista().getCabeza();
        while (actual != null) {
            if (actual.getDato().getPrioridad() > cpu.getProcesoActual().getPrioridad()) {
                return true;
            }
            actual = actual.getSiguiente();
        }
        return false;
    }

    /** Cambio de contexto: la politica elige un proceso de listos y se carga en la CPU (se sella su primer despacho si es la primera vez); con la cola vacia la CPU queda libre. */
    private void despacharSiguiente() {
        if (politica == null) {
            return;
        }
        Proceso siguiente = politica.seleccionar(colaListos);
        if (siguiente == null) {
            cpu.cargarProceso(null);
            cpu.setModo(ModoCPU.SISTEMA_OPERATIVO);
            return;
        }
        siguiente.setEstado(EstadoProceso.EJECUCION);
        if (siguiente.getCicloPrimerDespacho() < 0) {
            siguiente.setCicloPrimerDespacho(cicloActual);
        }
        cpu.cargarProceso(siguiente);
        quantumRestante = quantum;
        cpu.setModo(ModoCPU.USUARIO);
    }

    @Override
    public String toString() {
        String enCpu = (cpu.getProcesoActual() == null) ? "idle" : cpu.getProcesoActual().getEtiqueta();
        String pol = (nombrePolitica == null) ? "sin-politica" : nombrePolitica.toString();
        return "PC" + id + "-" + nombre
                + " [RAM " + ram.getMemoriaUsada() + "/" + ram.getCapacidadTotal()
                + ", CPU=" + enCpu
                + ", modo=" + cpu.getModo()
                + ", pol=" + pol
                + ", q=" + quantum
                + ", nuevos=" + colaNuevos.getTamanio()
                + ", listos=" + colaListos.getTamanio()
                + ", bloq=" + colaBloqueados.getTamanio()
                + ", term=" + colaTerminados.getTamanio() + "]";
    }
}

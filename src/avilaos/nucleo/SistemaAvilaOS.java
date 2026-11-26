package avilaos.nucleo;

import avilaos.archivos.DefinicionSistemaArchivos;
import avilaos.archivos.Permiso;
import avilaos.archivos.SistemaArchivos;
import avilaos.archivos.TipoNodo;
import avilaos.config.Configuracion;
import avilaos.disco.Disco;
import avilaos.disco.OperacionES;
import avilaos.disco.PoliticaDisco;
import avilaos.disco.SolicitudES;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.politicas.FabricaPoliticas;
import avilaos.nucleo.programas.ProgramaArchivo;

/**
 * ÁvilaOS del Proyecto 2: un único computador con su disco local, su sistema de archivos y el controlador que los une,
 * dentro de un Cluster de un solo computador (así el reloj, el log y el cerrojo del Proyecto 1 siguen sirviendo igual).
 * La interfaz solo observa y controla este objeto: la lógica del sistema vive aquí y en las clases que reúne.
 */
public class SistemaAvilaOS {

    /** Mayor prioridad que se acepta al crear un proceso. */
    public static final int MAX_PRIORIDAD = 100;
    /** Mayor deadline que se acepta, en ciclos. */
    public static final int MAX_DEADLINE = 1_000_000;

    /** Cluster de un solo computador: aporta el reloj global, el log y el cerrojo. */
    private final Cluster cluster;
    /** El computador (CPU, RAM y núcleo con sus colas, incluidas las de suspendidos). */
    private final Computador computador;
    /** Disco local, que también reacciona al reloj. */
    private final Disco disco;
    /** Sistema de archivos jerárquico sobre el disco. */
    private final SistemaArchivos archivos;
    /** Convierte las operaciones de los procesos en solicitudes de disco y aplica su efecto al terminar. */
    private final ControladorDisco controlador;

    private SistemaAvilaOS(Cluster cluster, Computador computador, Disco disco, SistemaArchivos archivos,
                           ControladorDisco controlador) {
        this.cluster = cluster;
        this.computador = computador;
        this.disco = disco;
        this.archivos = archivos;
        this.controlador = controlador;
    }

    /**
     * Arma el sistema: reinicia los contadores de ids, crea el disco con el tamaño y el cabezal del JSON del sistema de
     * archivos, carga sus archivos y conecta todo a un computador con la política de CPU dada.
     *
     * @param definicion el sistema de archivos leído del JSON (tamaño del disco, cabezal inicial y entradas)
     * @param velocidadCabezal posiciones que recorre el cabezal por ciclo
     */
    public static SistemaAvilaOS construir(DefinicionSistemaArchivos definicion, int ramTotal, int quantum,
                                           PoliticaPlanificacion politica, int bloquesSwap, PoliticaDisco politicaDisco,
                                           int velocidadCabezal) {
        if (politica == null || politicaDisco == null) {
            throw new IllegalArgumentException("Faltan las políticas de planificación.");
        }
        if (ramTotal < Configuracion.MIN_RAM || ramTotal > Configuracion.MAX_RAM) {
            throw new IllegalArgumentException("La RAM debe estar entre " + Configuracion.MIN_RAM + " y " + Configuracion.MAX_RAM + ".");
        }
        if (quantum < Configuracion.MIN_QUANTUM || quantum > Configuracion.MAX_QUANTUM) {
            throw new IllegalArgumentException("El quantum debe estar entre " + Configuracion.MIN_QUANTUM + " y " + Configuracion.MAX_QUANTUM + ".");
        }
        if (bloquesSwap < 0 || bloquesSwap > DefinicionSistemaArchivos.MAX_BLOQUES) {
            throw new IllegalArgumentException("El área de intercambio debe estar entre 0 y " + DefinicionSistemaArchivos.MAX_BLOQUES + " bloques.");
        }
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();
        SolicitudES.reiniciarContador();

        Disco disco = new Disco(definicion.bloquesTotales(), bloquesSwap, definicion.cabezalInicial(), velocidadCabezal, politicaDisco);
        SistemaArchivos archivos = new SistemaArchivos(disco);
        archivos.cargar(definicion.entradas());

        Cluster cluster = new Cluster();
        Computador computador = new Computador("PC1", ramTotal, quantum);
        computador.setPolitica(FabricaPoliticas.crear(politica), politica);
        cluster.agregarComputador(computador);
        computador.conectarDisco(disco, new MenorPrioridadMasBloqueado());
        ControladorDisco controlador = new ControladorDisco(computador, disco, archivos);
        cluster.agregarComponente(disco);
        disco.setRegistro(cluster::registrarEvento);
        return new SistemaAvilaOS(cluster, computador, disco, archivos, controlador);
    }

    public Cluster getCluster() {
        return cluster;
    }

    public Computador getComputador() {
        return computador;
    }

    public Disco getDisco() {
        return disco;
    }

    public SistemaArchivos getArchivos() {
        return archivos;
    }

    public ControladorDisco getControlador() {
        return controlador;
    }

    /**
     * Crea un proceso de E/S sobre un archivo o directorio, con la operación y la ruta en su PCB. Valida todos los
     * campos y lanza IllegalArgumentException con un mensaje claro si alguno es inválido. No lo entrega al computador:
     * eso lo hace iniciar.
     *
     * @param usuario quién lo pide (determina qué le está permitido; admin si es null)
     * @param tipoNuevo, bloquesNuevo, permisoNuevo solo se usan al crear
     * @param nuevoNombre solo se usa al actualizar; null si solo reescribe el archivo
     */
    public Proceso crearProcesoArchivo(String nombre, int prioridad, int memoria, int deadline, int instrucciones,
                                       OperacionES operacion, String ruta, String usuario, TipoNodo tipoNuevo,
                                       int bloquesNuevo, Permiso permisoNuevo, String nuevoNombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del proceso no puede estar vacío.");
        }
        if (nombre.contains(",")) {
            throw new IllegalArgumentException("El nombre del proceso no puede contener comas.");
        }
        if (operacion == null || operacion == OperacionES.SUSPENDER || operacion == OperacionES.REACTIVAR) {
            throw new IllegalArgumentException("Elija una operación: crear, leer, actualizar o eliminar.");
        }
        if (prioridad < 1 || prioridad > MAX_PRIORIDAD) {
            throw new IllegalArgumentException("La prioridad debe estar entre 1 y " + MAX_PRIORIDAD + ".");
        }
        if (memoria < 1 || memoria > computador.getRamTotal()) {
            throw new IllegalArgumentException("La memoria debe estar entre 1 y " + computador.getRamTotal() + " (la RAM del computador).");
        }
        if (deadline < 1 || deadline > MAX_DEADLINE) {
            throw new IllegalArgumentException("El deadline debe estar entre 1 y " + MAX_DEADLINE + " ciclos.");
        }
        if (instrucciones < 1 || instrucciones > Configuracion.MAX_INSTRUCCIONES) {
            throw new IllegalArgumentException("Las instrucciones deben estar entre 1 y " + Configuracion.MAX_INSTRUCCIONES + ".");
        }
        String rutaLimpia = SistemaArchivos.normalizar(ruta);
        if (operacion == OperacionES.CREAR && tipoNuevo == TipoNodo.ARCHIVO
                && (bloquesNuevo < 1 || bloquesNuevo > disco.getBloquesArchivos())) {
            throw new IllegalArgumentException("El archivo debe medir entre 1 y " + disco.getBloquesArchivos() + " bloques.");
        }
        if (operacion == OperacionES.ACTUALIZAR && nuevoNombre != null && !nuevoNombre.isBlank()) {
            SistemaArchivos.validarNombre(nuevoNombre);
        }
        Proceso proceso = new Proceso(nombre, TipoProceso.IO_BOUND, prioridad, memoria, deadline, instrucciones);
        proceso.setOperacionES(operacion);
        proceso.setRutaArchivo(rutaLimpia);
        proceso.setUsuario(usuario == null || usuario.isBlank() ? "admin" : usuario.trim());
        proceso.setPrograma(new ProgramaArchivo(controlador, tipoNuevo, bloquesNuevo, permisoNuevo, nuevoNombre));
        return proceso;
    }

    /** Entrega el proceso al computador: entra a la cola de nuevos y se admite si cabe en la RAM. */
    public void iniciar(Proceso proceso) {
        cluster.asignarProcesoManual(proceso, computador.getId());
    }

    /** Avanza un ciclo sin hilos (computador y luego disco); lo usan las pruebas y los experimentos. */
    public void avanzar(int ciclo) {
        cluster.onTickGlobal(ciclo);
    }
}

package avilaos.modelo;

import avilaos.contratos.ProgramaProceso;
import avilaos.sincronizacion.SemaforoSO;

/**
 * Bloque de control de proceso (PCB). Como cada proceso tiene un unico hilo, el PCB y el TCB coinciden: aqui
 * estan los datos del proceso (id, estado, prioridad, memoria, deadline...) y su contexto de ejecucion (pc).
 * Quien modifica un Proceso es siempre el hilo de su computador, salvo semaforoBloqueo que tambien lo
 * escribe el semaforo (con su candado) cuando otro proceso hace semSignal.
 */
public class Proceso {

    /** Contador para generar IDs unicos en todo el sistema distribuido. Se reinicia solo al construir un cluster nuevo. */
    private static int contadorGlobal = 0;

    /** ID unico en todo el sistema distribuido, generado dinamicamente al crear el proceso. */
    private final int id;
    /** Nombre dado por el usuario; con el id forma la etiqueta (por ejemplo P3-calc). */
    private String nombre;
    /** Id del computador donde corre; -1 hasta que se le asigna uno. Una vez asignado no cambia (no hay migracion). */
    private int computadorId;
    /** Estado actual del proceso. */
    private EstadoProceso estado;
    /** Tipo de proceso (CPU bound, I/O bound, productor o consumidor). */
    private TipoProceso tipo;
    /** Prioridad: un numero mayor significa mas importante (la usa la politica de prioridades). */
    private int prioridad;
    /** Unidades de RAM que necesita para ser admitido en Listos. */
    private int memoriaRequerida;
    /** Ciclos que le quedan antes de ser terminado por el sistema; baja un ciclo por cada ciclo de reloj. */
    private int deadline;
    /** Instrucciones que le faltan; al llegar a 0 el proceso termina. */
    private int tiempoRestante;
    /** Contador de programa: instrucciones ejecutadas (ejecucion lineal, sube una unidad por instruccion). */
    private int pc;
    /** Texto que explica por que esta bloqueado (se muestra en la interfaz); null si no lo esta. */
    private String motivoBloqueo;
    /** Ciclo global en que llego a su computador (para el tiempo de respuesta); -1 si aun no llega. */
    private int cicloLlegada = -1;
    /** Ciclo en que se le dio la CPU por primera vez; -1 si nunca la tuvo. */
    private int cicloPrimerDespacho = -1;
    /** Ciclo en que termino; -1 si sigue vivo. */
    private int cicloFin = -1;
    /**
     * Semaforo en cuya cola de espera esta el proceso; null si no esta en ninguna. volatile porque lo escribe
     * el semaforo desde el hilo de otro computador al despertarlo, y lo lee el hilo del computador dueno.
     */
    private volatile SemaforoSO semaforoBloqueo;
    /** El codigo que ejecuta el proceso, uno por ciclo; null equivale a un proceso CPU bound. */
    private ProgramaProceso programa;
    /** Ciclos que le faltan a un bloqueo por tiempo (E/S o red); -1 si no es de esa clase. */
    private int bloqueoRestante = -1;
    /** Ciclos acumulados bloqueado en colas de semaforos (metrica de tiempo bloqueado). */
    private int ciclosBloqueadoSemaforo;

    /** Crea un proceso nuevo con el estado NUEVO; valida que el nombre no este vacio y que los numeros no sean negativos. */
    public Proceso(String nombre, TipoProceso tipo, int prioridad,
                   int memoriaRequerida, int deadline, int tiempoRestante) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del proceso no puede estar vacío.");
        }
        if (memoriaRequerida < 0 || deadline < 0 || tiempoRestante < 0) {
            throw new IllegalArgumentException("Memoria, deadline y tiempo restante no pueden ser negativos.");
        }

        this.id = ++contadorGlobal;
        this.nombre = nombre.trim();
        this.computadorId = -1;
        this.estado = EstadoProceso.NUEVO;
        this.tipo = tipo;
        this.prioridad = prioridad;
        this.memoriaRequerida = memoriaRequerida;
        this.deadline = deadline;
        this.tiempoRestante = tiempoRestante;
        this.pc = 0;
    }

    /** ID unico e inmutable del proceso. Los getters y setters que siguen son accesos simples a los campos del PCB. */
    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getComputadorId() {
        return computadorId;
    }

    public void setComputadorId(int computadorId) {
        this.computadorId = computadorId;
    }

    public EstadoProceso getEstado() {
        return estado;
    }

    public void setEstado(EstadoProceso estado) {
        this.estado = estado;
    }

    public TipoProceso getTipo() {
        return tipo;
    }

    public void setTipo(TipoProceso tipo) {
        this.tipo = tipo;
    }

    public int getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(int prioridad) {
        this.prioridad = prioridad;
    }

    public int getMemoriaRequerida() {
        return memoriaRequerida;
    }

    public void setMemoriaRequerida(int memoriaRequerida) {
        this.memoriaRequerida = memoriaRequerida;
    }

    public int getDeadline() {
        return deadline;
    }

    public void setDeadline(int deadline) {
        this.deadline = deadline;
    }

    public int getTiempoRestante() {
        return tiempoRestante;
    }

    public void setTiempoRestante(int tiempoRestante) {
        this.tiempoRestante = tiempoRestante;
    }

    public int getPc() {
        return pc;
    }

    public void setPc(int pc) {
        this.pc = pc;
    }

    /** Ciclo global en que el proceso llego a su computador; -1 si aun no llega. */
    public int getCicloLlegada() {
        return cicloLlegada;
    }

    public void setCicloLlegada(int cicloLlegada) {
        this.cicloLlegada = cicloLlegada;
    }

    /** Ciclo global en que fue despachado a la CPU por primera vez; -1 si nunca lo fue. */
    public int getCicloPrimerDespacho() {
        return cicloPrimerDespacho;
    }

    public void setCicloPrimerDespacho(int cicloPrimerDespacho) {
        this.cicloPrimerDespacho = cicloPrimerDespacho;
    }

    /** Ciclo global en que termino; -1 si sigue vivo. */
    public int getCicloFin() {
        return cicloFin;
    }

    public void setCicloFin(int cicloFin) {
        this.cicloFin = cicloFin;
    }

    /** Un proceso cumple su deadline si ejecuto todas sus instrucciones antes de que llegara a 0. */
    public boolean cumplioDeadline() {
        return estado == EstadoProceso.TERMINADO && tiempoRestante <= 0;
    }

    /** Ciclos que el proceso ha ejecutado en la CPU. */
    public int getCiclosEjecutados() {
        return pc;
    }

    /** Codigo que ejecuta el proceso; null equivale a un proceso CPU bound. */
    public ProgramaProceso getPrograma() {
        return programa;
    }

    public void setPrograma(ProgramaProceso programa) {
        this.programa = programa;
    }

    /** Ciclos que le faltan a un bloqueo por tiempo (E/S o red); -1 si no es un bloqueo por tiempo. */
    public int getBloqueoRestante() {
        return bloqueoRestante;
    }

    public void setBloqueoRestante(int bloqueoRestante) {
        this.bloqueoRestante = bloqueoRestante;
    }

    /** Ciclos acumulados que el proceso ha pasado bloqueado en colas de semaforos. */
    public int getCiclosBloqueadoSemaforo() {
        return ciclosBloqueadoSemaforo;
    }

    /** Suma un ciclo de tiempo bloqueado en un semaforo (lo llama el computador por cada ciclo que el proceso espera). */
    public void sumarCicloBloqueadoSemaforo() {
        ciclosBloqueadoSemaforo++;
    }

    /** Semaforo en cuya cola espera el proceso; null si no esta bloqueado en ninguno. */
    public SemaforoSO getSemaforoBloqueo() {
        return semaforoBloqueo;
    }

    public void setSemaforoBloqueo(SemaforoSO semaforoBloqueo) {
        this.semaforoBloqueo = semaforoBloqueo;
    }

    /** Texto que explica por que esta bloqueado (por ejemplo el semaforo); null si no lo esta. */
    public String getMotivoBloqueo() {
        return motivoBloqueo;
    }

    public void setMotivoBloqueo(String motivoBloqueo) {
        this.motivoBloqueo = motivoBloqueo;
    }

    /** Etiqueta corta para el log y las tablas: P + id + nombre. */
    public String getEtiqueta() {
        return "P" + id + "-" + nombre;
    }

    /** Resta un ciclo al deadline y devuelve true si llego a 0; el computador es quien lo termina. */
    public boolean descontarDeadline() {
        if (deadline > 0) {
            deadline--;
        }
        return deadline <= 0;
    }

    /** Ejecuta una instruccion: baja el tiempo restante, sube el PC y, si no quedan instrucciones, pasa a TERMINADO. */
    public void ejecutarUnCiclo() {
        if (tiempoRestante > 0) {
            tiempoRestante--;
            pc++;
        }
        if (tiempoRestante <= 0) {
            estado = EstadoProceso.TERMINADO;
        }
    }

    /** Verdadero si ya esta en estado TERMINADO. */
    public boolean estaTerminado() {
        return estado == EstadoProceso.TERMINADO;
    }

    /** Reinicia el contador de IDs (se usa al construir un cluster nuevo y en las pruebas). */
    public static void reiniciarContadorGlobal() {
        contadorGlobal = 0;
    }

    /** Dos procesos son el mismo si tienen el mismo id (permite buscarlos y quitarlos de las listas). */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Proceso otro)) {
            return false;
        }
        return this.id == otro.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return getEtiqueta()
                + " [" + estado
                + ", pri=" + prioridad
                + ", dl=" + deadline
                + ", rest=" + tiempoRestante
                + ", pc=" + pc + "]";
    }
}

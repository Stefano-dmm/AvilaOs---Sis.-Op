package avilaos.modelo;

import avilaos.sincronizacion.SemaforoSO;

public class Proceso {

    private static int contadorGlobal = 0;

    private final int id;
    private String nombre;
    private int computadorId;
    private EstadoProceso estado;
    private TipoProceso tipo;
    private int prioridad;
    private int memoriaRequerida;
    private int deadline;
    private int tiempoRestante;
    private int pc;
    private String motivoBloqueo;
    private int cicloLlegada = -1;
    private int cicloPrimerDespacho = -1;
    private int cicloFin = -1;
    private volatile SemaforoSO semaforoBloqueo;

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

    public String getEtiqueta() {
        return "P" + id + "-" + nombre;
    }

    public boolean decrementarDeadline() {
        if (deadline > 0) {
            deadline--;
        }
        if (deadline <= 0) {
            estado = EstadoProceso.TERMINADO;
            return true;
        }
        return false;
    }

    public void ejecutarUnCiclo() {
        if (tiempoRestante > 0) {
            tiempoRestante--;
            pc++;
        }
        if (tiempoRestante <= 0) {
            estado = EstadoProceso.TERMINADO;
        }
    }

    public boolean estaTerminado() {
        return estado == EstadoProceso.TERMINADO;
    }

    public static void reiniciarContadorGlobal() {
        contadorGlobal = 0;
    }

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

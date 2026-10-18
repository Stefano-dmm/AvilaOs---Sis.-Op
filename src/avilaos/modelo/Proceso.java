package avilaos.modelo;

/**
 * Bloque de control del proceso (PCB).
 * Modelo monohilo: un proceso = un hilo de ejecución.
 */
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

    public Proceso(String nombre, TipoProceso tipo, int prioridad,
                   int memoriaRequerida, int deadline, int tiempoRestante) {
        this.id = ++contadorGlobal;
        this.nombre = nombre;
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

    /** Baja el deadline en 1. Si llega a 0 o menos, el proceso termina. */
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

    /** Baja el tiempo restante en 1 e incrementa el PC. */
    public void ejecutarUnCiclo() {
        if (tiempoRestante > 0) {
            tiempoRestante--;
            pc++;
        }
        if (tiempoRestante <= 0) {
            estado = EstadoProceso.TERMINADO;
        }
    }

    public static void reiniciarContadorGlobal() {
        contadorGlobal = 0;
    }

    @Override
    public String toString() {
        return "P" + id + "-" + nombre
                + " [" + estado
                + ", pri=" + prioridad
                + ", dl=" + deadline
                + ", rest=" + tiempoRestante + "]";
    }
}

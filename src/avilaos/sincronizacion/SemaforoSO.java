package avilaos.sincronizacion;

import avilaos.estructuras.Cola;
import avilaos.estructuras.Nodo;
import avilaos.modelo.Proceso;
import java.util.concurrent.Semaphore;

/**
 * Semaforo del sistema operativo AvilaOS. No bloquea hilos de Java: cuando un proceso no puede
 * continuar, semWait lo deja en la cola del semaforo y quien llama lo pasa a Bloqueado para que
 * la CPU atienda a otro. El estado interno se protege con un Semaphore de Java (exclusion mutua),
 * porque procesos de distintos computadores (distintos hilos) pueden operarlo a la vez.
 */
public class SemaforoSO {

    private final String nombre;
    private final TipoSemaforo tipo;
    private int valor;
    private final Cola<Proceso> espera = new Cola<>();
    private final Semaphore candado = new Semaphore(1);

    public SemaforoSO(String nombre, TipoSemaforo tipo, int valorInicial) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El semaforo necesita un nombre.");
        }
        if (valorInicial < 0 || (tipo == TipoSemaforo.BINARIO && valorInicial > 1)) {
            throw new IllegalArgumentException("Valor inicial invalido para el semaforo " + nombre);
        }
        this.nombre = nombre;
        this.tipo = tipo;
        this.valor = valorInicial;
    }

    /**
     * Operacion semWait: decrementa el valor. Devuelve true si el proceso puede continuar, o false
     * si quedo en la cola del semaforo (el valor negativo cuenta cuantos esperan).
     */
    public boolean semWait(Proceso proceso) {
        if (proceso == null) {
            throw new IllegalArgumentException("El proceso no puede ser null.");
        }
        candado.acquireUninterruptibly();
        try {
            valor--;
            if (valor < 0) {
                proceso.setSemaforoBloqueo(this);
                espera.encolar(proceso);
                return false;
            }
            return true;
        } finally {
            candado.release();
        }
    }

    /**
     * Operacion semSignal: incrementa el valor. Si habia procesos esperando, devuelve el primero
     * (el que debe despertarse); si no, devuelve null. Un semaforo binario no pasa de 1.
     */
    public Proceso semSignal() {
        candado.acquireUninterruptibly();
        try {
            if (tipo == TipoSemaforo.BINARIO && valor >= 1) {
                return null;
            }
            valor++;
            if (valor <= 0) {
                Proceso despertado = espera.desencolar();
                if (despertado != null) {
                    despertado.setSemaforoBloqueo(null);
                }
                return despertado;
            }
            return null;
        } finally {
            candado.release();
        }
    }

    /** Saca a un proceso de la cola (por ejemplo si termina por deadline) y devuelve la unidad. */
    public boolean cancelarEspera(Proceso proceso) {
        candado.acquireUninterruptibly();
        try {
            boolean estaba = espera.getLista().eliminar(proceso);
            if (estaba) {
                valor++;
                proceso.setSemaforoBloqueo(null);
            }
            return estaba;
        } finally {
            candado.release();
        }
    }

    public String getNombre() {
        return nombre;
    }

    public TipoSemaforo getTipo() {
        return tipo;
    }

    public int getValor() {
        candado.acquireUninterruptibly();
        try {
            return valor;
        } finally {
            candado.release();
        }
    }

    public int getCantidadEnEspera() {
        candado.acquireUninterruptibly();
        try {
            return espera.getTamanio();
        } finally {
            candado.release();
        }
    }

    /** Etiquetas de los procesos en espera, separadas por coma; cadena vacia si no hay ninguno. */
    public String describirEspera() {
        candado.acquireUninterruptibly();
        try {
            StringBuilder sb = new StringBuilder();
            Nodo<Proceso> actual = espera.getLista().getCabeza();
            while (actual != null) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(actual.getDato().getEtiqueta());
                actual = actual.getSiguiente();
            }
            return sb.toString();
        } finally {
            candado.release();
        }
    }

    @Override
    public String toString() {
        return nombre + "=" + getValor();
    }
}

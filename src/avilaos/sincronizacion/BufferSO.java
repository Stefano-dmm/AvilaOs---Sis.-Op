package avilaos.sincronizacion;

import avilaos.nucleo.Computador;
import java.util.concurrent.Semaphore;

/**
 * Buffer acotado del productor-consumidor. Vive en la RAM de su computador anfitrion y se
 * sincroniza con tres semaforos (algoritmo clasico de Stallings): vacios cuenta los huecos,
 * llenos cuenta los elementos y mutex da exclusion mutua a la region critica.
 */
public class BufferSO {

    /** Unidades de RAM que ocupa cada hueco del buffer en su anfitrion. */
    public static final int TAMANO_ELEMENTO = 4;

    private final int id;
    private final String nombre;
    private final int capacidad;
    private final Computador anfitrion;
    private final SemaforoSO vacios;
    private final SemaforoSO llenos;
    private final SemaforoSO mutex;
    private final Semaphore candado = new Semaphore(1);
    private int elementos;
    private int producidos;
    private int consumidos;

    public BufferSO(int id, String nombre, int capacidad, Computador anfitrion) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El buffer necesita un nombre.");
        }
        if (capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad del buffer debe ser mayor que 0.");
        }
        if (anfitrion == null) {
            throw new IllegalArgumentException("El buffer necesita un computador anfitrion.");
        }
        this.id = id;
        this.nombre = nombre.trim();
        this.capacidad = capacidad;
        this.anfitrion = anfitrion;
        this.vacios = new SemaforoSO("vacios", TipoSemaforo.CONTEO, capacidad);
        this.llenos = new SemaforoSO("llenos", TipoSemaforo.CONTEO, 0);
        this.mutex = new SemaforoSO("mutex", TipoSemaforo.BINARIO, 1);
    }

    /** Debe llamarse dentro de la region critica (con el mutex tomado por el proceso). */
    public void insertar() {
        candado.acquireUninterruptibly();
        try {
            if (elementos >= capacidad) {
                throw new IllegalStateException("Se inserto en el buffer " + nombre + " estando lleno.");
            }
            elementos++;
            producidos++;
        } finally {
            candado.release();
        }
    }

    /** Debe llamarse dentro de la region critica (con el mutex tomado por el proceso). */
    public void extraer() {
        candado.acquireUninterruptibly();
        try {
            if (elementos <= 0) {
                throw new IllegalStateException("Se extrajo del buffer " + nombre + " estando vacio.");
            }
            elementos--;
            consumidos++;
        } finally {
            candado.release();
        }
    }

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public Computador getAnfitrion() {
        return anfitrion;
    }

    public int getMemoria() {
        return capacidad * TAMANO_ELEMENTO;
    }

    public SemaforoSO getVacios() {
        return vacios;
    }

    public SemaforoSO getLlenos() {
        return llenos;
    }

    public SemaforoSO getMutex() {
        return mutex;
    }

    public int getElementos() {
        candado.acquireUninterruptibly();
        try {
            return elementos;
        } finally {
            candado.release();
        }
    }

    public int getProducidos() {
        candado.acquireUninterruptibly();
        try {
            return producidos;
        } finally {
            candado.release();
        }
    }

    public int getConsumidos() {
        candado.acquireUninterruptibly();
        try {
            return consumidos;
        } finally {
            candado.release();
        }
    }

    @Override
    public String toString() {
        return nombre + " [" + getElementos() + "/" + capacidad + " en " + anfitrion.getNombre() + "]";
    }
}

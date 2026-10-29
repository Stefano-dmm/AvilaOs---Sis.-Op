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

    /** Numero de creacion del buffer, unico en el cluster. */
    private final int id;
    /** Nombre mostrado en la interfaz y en el log (por ejemplo B1). */
    private final String nombre;
    /** Cantidad maxima de elementos; el buffer ocupa capacidad * TAMANO_ELEMENTO unidades de RAM del anfitrion. */
    private final int capacidad;
    /** Computador en cuya RAM vive el buffer. Los procesos de otros computadores acceden de forma remota (con latencia de red). */
    private final Computador anfitrion;
    /** Semaforo de CONTEO de huecos libres; arranca en capacidad. El productor hace semWait(vacios) antes de insertar. */
    private final SemaforoSO vacios;
    /** Semaforo de CONTEO de elementos disponibles; arranca en 0. El consumidor hace semWait(llenos) antes de extraer. */
    private final SemaforoSO llenos;
    /**
     * Semaforo BINARIO que da exclusion mutua a la region critica simulada (insertar y extraer). Es lo que
     * protege el buffer entre los PROCESOS simulados; no es el mismo que candado.
     */
    private final SemaforoSO mutex;
    /**
     * Semaphore de Java que protege los contadores elementos, producidos y consumidos para que otros hilos (la
     * interfaz o las metricas) los lean sin ver valores a medias. La exclusion entre procesos simulados la da
     * el semaforo mutex, no este candado.
     */
    private final Semaphore candado = new Semaphore(1);
    /** Elementos que hay ahora en el buffer (0 a capacidad). Protegido por candado. */
    private int elementos;
    /** Total de elementos insertados desde que existe el buffer (metrica). Protegido por candado. */
    private int producidos;
    /** Total de elementos extraidos desde que existe el buffer (metrica). Protegido por candado. */
    private int consumidos;

    /** Crea el buffer con sus tres semaforos: vacios en capacidad, llenos en 0 y mutex en 1. */
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

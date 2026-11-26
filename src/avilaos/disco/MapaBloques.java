package avilaos.disco;

/**
 * Mapa de los bloques del disco con asignación encadenada: cada archivo es una lista enlazada de bloques y cada
 * bloque guarda cuál es el siguiente. Un bloque libre no tiene dueño. No es seguro entre hilos: lo protege el
 * candado del Disco que lo posee.
 */
public class MapaBloques {

    /** Marca de "no hay siguiente": el último bloque de una cadena. */
    public static final int FIN_DE_CADENA = -1;
    /** Dueño de un bloque libre. */
    public static final int LIBRE = 0;

    /** Cantidad total de bloques. */
    private final int total;
    /** Para cada bloque, el número del siguiente de su archivo, o FIN_DE_CADENA. */
    private final int[] siguiente;
    /** Para cada bloque, el dueño: LIBRE, el id de un archivo (positivo) o el id negativo de un proceso suspendido (swap). */
    private final int[] dueno;
    /** Bloques libres ahora. */
    private int libres;

    /** Crea un mapa con todos los bloques libres; necesita al menos un bloque. */
    public MapaBloques(int total) {
        if (total < 1) {
            throw new IllegalArgumentException("El disco necesita al menos un bloque.");
        }
        this.total = total;
        this.siguiente = new int[total];
        this.dueno = new int[total];
        this.libres = total;
        for (int i = 0; i < total; i++) {
            siguiente[i] = FIN_DE_CADENA;
        }
    }

    /**
     * Asigna n bloques libres del rango [desde, hasta) encadenándolos en orden (primer ajuste) y devuelve el primero;
     * -1 si en ese rango no hay n bloques libres. Los bloques pueden no ser contiguos.
     */
    public int asignar(int n, int duenoId, int desde, int hasta) {
        if (n < 1 || duenoId == LIBRE || desde < 0 || hasta > total || desde >= hasta) {
            throw new IllegalArgumentException("Asignación de bloques inválida.");
        }
        if (libresEn(desde, hasta) < n) {
            return -1;
        }
        int primero = -1;
        int anterior = -1;
        int puestos = 0;
        for (int b = desde; b < hasta && puestos < n; b++) {
            if (dueno[b] == LIBRE) {
                dueno[b] = duenoId;
                siguiente[b] = FIN_DE_CADENA;
                if (anterior >= 0) {
                    siguiente[anterior] = b;
                } else {
                    primero = b;
                }
                anterior = b;
                puestos++;
            }
        }
        libres -= n;
        return primero;
    }

    /** Asigna n bloques contiguos a partir de inicio (lo usa la carga del JSON); false si alguno está fuera del disco u ocupado. */
    public boolean asignarContiguos(int inicio, int n, int duenoId) {
        if (n < 1 || duenoId == LIBRE || inicio < 0 || inicio + n > total) {
            return false;
        }
        for (int b = inicio; b < inicio + n; b++) {
            if (dueno[b] != LIBRE) {
                return false;
            }
        }
        for (int b = inicio; b < inicio + n; b++) {
            dueno[b] = duenoId;
            siguiente[b] = (b == inicio + n - 1) ? FIN_DE_CADENA : b + 1;
        }
        libres -= n;
        return true;
    }

    /** Libera la cadena que empieza en primero y devuelve cuántos bloques liberó. */
    public int liberar(int primero) {
        int liberados = 0;
        int actual = primero;
        while (actual >= 0 && actual < total && dueno[actual] != LIBRE) {
            int proximo = siguiente[actual];
            dueno[actual] = LIBRE;
            siguiente[actual] = FIN_DE_CADENA;
            liberados++;
            actual = proximo;
        }
        libres += liberados;
        return liberados;
    }

    /** Bloques libres dentro del rango [desde, hasta). */
    public int libresEn(int desde, int hasta) {
        int cuenta = 0;
        for (int b = desde; b < hasta; b++) {
            if (dueno[b] == LIBRE) {
                cuenta++;
            }
        }
        return cuenta;
    }

    public int getTotal() {
        return total;
    }

    public int getLibres() {
        return libres;
    }

    /** Dueño del bloque (LIBRE si nadie lo usa). */
    public int getDueno(int bloque) {
        return dueno[bloque];
    }

    /** Siguiente bloque en la cadena del bloque dado, o FIN_DE_CADENA. */
    public int getSiguiente(int bloque) {
        return siguiente[bloque];
    }

    /** Recorre la cadena desde primero y devuelve cuántos bloques tiene. */
    public int contarCadena(int primero) {
        int cuenta = 0;
        int actual = primero;
        while (actual >= 0 && actual < total && cuenta <= total) {
            cuenta++;
            actual = siguiente[actual];
        }
        return cuenta;
    }
}

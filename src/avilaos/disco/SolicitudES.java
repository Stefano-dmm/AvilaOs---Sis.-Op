package avilaos.disco;

/**
 * Una solicitud de E/S en la cola del disco. La genera un proceso de E/S (o el planificador a mediano plazo
 * al suspender o reactivar) y el disco la atiende de a una. Guarda lo que la política necesita para ordenarla
 * (el bloque destino) y lo que el disco necesita para atenderla (cuántos bloques transfiere).
 */
public class SolicitudES {

    /** Contador para numerar las solicitudes en orden de creación. */
    private static int contador = 0;

    /** Número de la solicitud, único y creciente. */
    private final int id;
    /** Operación pedida. */
    private final OperacionES operacion;
    /** Bloque al que debe llegar el cabezal (el primero de la cadena del archivo o del swap). */
    private final int bloque;
    /** Bloques que se leen o escriben una vez que el cabezal llegó (cada uno cuesta un ciclo). Mínimo 1. */
    private final int bloquesATransferir;
    /** Id del proceso que pidió la operación (para despertarlo al terminar). */
    private final int procesoId;
    /** Etiqueta del proceso, para el log y la tabla de la cola. */
    private final String etiquetaProceso;
    /** Ruta del archivo o directorio sobre el que se opera; null en suspender y reactivar. */
    private final String ruta;
    /** Ciclo global en que entró a la cola (para el tiempo de espera). */
    private final int cicloLlegada;
    /** Ciclo en que el disco empezó a atenderla; -1 mientras espera. */
    private int cicloInicio = -1;
    /** Dato que el sistema operativo asocia a la solicitud para saber qué hacer cuando termina; el disco no lo usa. */
    private Object contexto;

    /** Crea la solicitud; el bloque no puede ser negativo y se transfiere al menos un bloque. */
    public SolicitudES(OperacionES operacion, int bloque, int bloquesATransferir, int procesoId,
                       String etiquetaProceso, String ruta, int cicloLlegada) {
        if (operacion == null) {
            throw new IllegalArgumentException("La solicitud necesita una operación.");
        }
        if (bloque < 0) {
            throw new IllegalArgumentException("El bloque de la solicitud no puede ser negativo.");
        }
        this.id = ++contador;
        this.operacion = operacion;
        this.bloque = bloque;
        this.bloquesATransferir = Math.max(1, bloquesATransferir);
        this.procesoId = procesoId;
        this.etiquetaProceso = etiquetaProceso;
        this.ruta = ruta;
        this.cicloLlegada = cicloLlegada;
    }

    public int getId() {
        return id;
    }

    public OperacionES getOperacion() {
        return operacion;
    }

    public int getBloque() {
        return bloque;
    }

    public int getBloquesATransferir() {
        return bloquesATransferir;
    }

    public int getProcesoId() {
        return procesoId;
    }

    public String getEtiquetaProceso() {
        return etiquetaProceso;
    }

    public String getRuta() {
        return ruta;
    }

    public int getCicloLlegada() {
        return cicloLlegada;
    }

    public int getCicloInicio() {
        return cicloInicio;
    }

    public void setCicloInicio(int cicloInicio) {
        this.cicloInicio = cicloInicio;
    }

    public Object getContexto() {
        return contexto;
    }

    public void setContexto(Object contexto) {
        this.contexto = contexto;
    }

    /** Reinicia la numeración (se usa al construir un sistema nuevo y en las pruebas). */
    public static void reiniciarContador() {
        contador = 0;
    }

    @Override
    public String toString() {
        return operacion + " bloque " + bloque + " (" + etiquetaProceso + ")";
    }
}

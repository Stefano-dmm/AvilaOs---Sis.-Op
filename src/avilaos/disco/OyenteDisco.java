package avilaos.disco;

/** Quien quiere enterarse de que el disco terminó de atender una solicitud (el sistema de archivos y el computador). */
public interface OyenteDisco {

    /** El disco terminó la solicitud en el ciclo dado. Se llama desde el hilo del disco, sin su candado tomado. */
    void solicitudAtendida(SolicitudES solicitud, int ciclo);
}

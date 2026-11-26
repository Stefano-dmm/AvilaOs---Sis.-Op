package avilaos.archivos;

import avilaos.disco.OperacionES;

/**
 * Reglas de qué puede hacer cada sesión. El administrador puede todo. El usuario solo lee archivos propios o públicos,
 * crea dentro de directorios propios y actualiza (reescribe) o elimina elementos propios; cambiar un nombre solo
 * lo hace el administrador. Cada método devuelve null si la operación se permite o el motivo del rechazo, para
 * que la interfaz se lo explique al usuario sin interrumpir la simulación.
 */
public final class ControlAcceso {

    /** Clase de utilidades: no se instancia. */
    private ControlAcceso() {
    }

    /**
     * Motivo por el que la sesión no puede hacer la operación sobre el objetivo, o null si puede.
     *
     * @param objetivo el elemento sobre el que se opera; en CREAR es el directorio donde se crea
     * @param cambiaNombre en ACTUALIZAR, verdadero si la operación renombra
     */
    public static String motivoDeRechazo(Sesion sesion, OperacionES operacion, NodoArchivo objetivo, boolean cambiaNombre) {
        if (sesion.esAdministrador()) {
            return null;
        }
        if (objetivo == null) {
            return "El elemento no existe.";
        }
        boolean propio = objetivo.getDueno().equals(sesion.usuario());
        return switch (operacion) {
            case LEER -> (propio || objetivo.getPermiso() == Permiso.PUBLICO) ? null
                    : objetivo.getRuta() + " es privado y pertenece a " + objetivo.getDueno() + ".";
            case CREAR -> (propio && objetivo.esDirectorio()) ? null
                    : "Un usuario solo puede crear dentro de sus propios directorios; " + objetivo.getRuta()
                    + " pertenece a " + objetivo.getDueno() + ".";
            case ACTUALIZAR -> cambiaNombre ? "Solo el administrador puede cambiar nombres."
                    : (propio ? null : "No se pueden modificar archivos de " + objetivo.getDueno() + ".");
            case ELIMINAR -> propio ? null : "No se pueden eliminar archivos de " + objetivo.getDueno() + ".";
            case SUSPENDER, REACTIVAR -> "Solo el sistema suspende o reactiva procesos.";
        };
    }
}

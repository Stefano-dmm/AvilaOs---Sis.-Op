package avilaos.nucleo.programas;

import avilaos.archivos.NodoArchivo;
import avilaos.archivos.Permiso;
import avilaos.archivos.TipoNodo;
import avilaos.contratos.ProgramaProceso;
import avilaos.modelo.ModoCPU;
import avilaos.modelo.Proceso;
import avilaos.modelo.ResultadoCiclo;
import avilaos.nucleo.Computador;
import avilaos.nucleo.ControladorDisco;

/**
 * Proceso de E/S sobre un archivo: hace sus instrucciones de usuario y en la última genera la solicitud al disco (una
 * instrucción en modo sistema operativo), se bloquea por E/S hasta que el disco la atiende y, al despertar, termina.
 * La operación y la ruta están en el PCB del proceso (Proceso.operacionES y Proceso.rutaArchivo); aquí solo están los
 * datos extra de crear (tipo, tamaño, permiso) y de actualizar (nombre nuevo).
 */
public class ProgramaArchivo implements ProgramaProceso {

    /** Punto del programa en que va el proceso. */
    private enum Paso {
        /** Instrucciones de usuario antes de pedir la E/S. */
        TRABAJAR,
        /** Ya pidió la E/S: al volver a la CPU solo le queda terminar. */
        COMPLETAR
    }

    /** Quien arma la solicitud y la manda al disco. */
    private final ControladorDisco controlador;
    /** Al crear: si es archivo o directorio. */
    private final TipoNodo tipoNuevo;
    /** Al crear un archivo: su tamaño en bloques. */
    private final int bloquesNuevo;
    /** Al crear: quién puede leerlo además de su dueño. */
    private final Permiso permisoNuevo;
    /** Al actualizar: el nombre nuevo; null si solo reescribe el archivo. */
    private final String nuevoNombre;
    /** Punto actual del programa. */
    private Paso paso = Paso.TRABAJAR;
    /** Archivo o directorio con bloques reservados cuya creación todavía no atiende el disco; null si no hay. */
    private NodoArchivo pendiente;

    /** Crea el programa; los datos de crear y de actualizar pueden ser null o 0 si la operación no los usa. */
    public ProgramaArchivo(ControladorDisco controlador, TipoNodo tipoNuevo, int bloquesNuevo, Permiso permisoNuevo,
                           String nuevoNombre) {
        if (controlador == null) {
            throw new IllegalArgumentException("El proceso de archivos necesita el controlador del disco.");
        }
        this.controlador = controlador;
        this.tipoNuevo = tipoNuevo == null ? TipoNodo.ARCHIVO : tipoNuevo;
        this.bloquesNuevo = bloquesNuevo;
        this.permisoNuevo = permisoNuevo == null ? Permiso.PRIVADO : permisoNuevo;
        this.nuevoNombre = (nuevoNombre == null || nuevoNombre.isBlank()) ? null : nuevoNombre.trim();
    }

    public TipoNodo getTipoNuevo() {
        return tipoNuevo;
    }

    public int getBloquesNuevo() {
        return bloquesNuevo;
    }

    public Permiso getPermisoNuevo() {
        return permisoNuevo;
    }

    public String getNuevoNombre() {
        return nuevoNombre;
    }

    @Override
    public ResultadoCiclo ejecutar(Computador computador, Proceso proceso) {
        if (paso == Paso.COMPLETAR) {
            computador.setModoCPU(ModoCPU.SISTEMA_OPERATIVO);
            return ResultadoCiclo.TERMINADO;
        }
        if (proceso.getTiempoRestante() > 1) {
            computador.setModoCPU(ModoCPU.USUARIO);
            proceso.ejecutarUnCiclo();
            return ResultadoCiclo.CONTINUA;
        }
        // Última instrucción: la operación sobre el sistema de archivos, en modo sistema operativo.
        computador.setModoCPU(ModoCPU.SISTEMA_OPERATIVO);
        proceso.setTiempoRestante(0);
        proceso.setPc(proceso.getPc() + 1);
        paso = Paso.COMPLETAR;
        if (!controlador.pedir(proceso, this)) {
            return ResultadoCiclo.TERMINADO;
        }
        computador.bloquearPorDisco(proceso, "E/S de disco: " + proceso.getOperacionES() + " " + proceso.getRutaArchivo());
        return ResultadoCiclo.BLOQUEADO;
    }

    /** Lo que el controlador reservó para una creación que el disco todavía no atendió; si el proceso muere se descarta. */
    public void setPendiente(NodoArchivo pendiente) {
        this.pendiente = pendiente;
    }

    /** Si el proceso muere antes de que el disco atienda su creación, se descartan los bloques que había reservado. */
    @Override
    public void alTerminar(Computador computador, Proceso proceso) {
        if (pendiente != null) {
            controlador.getArchivos().cancelarCreacion(pendiente);
        }
    }
}

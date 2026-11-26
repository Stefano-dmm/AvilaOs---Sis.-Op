package avilaos.nucleo;

import avilaos.archivos.ControlAcceso;
import avilaos.archivos.NodoArchivo;
import avilaos.archivos.Permiso;
import avilaos.archivos.Sesion;
import avilaos.archivos.SistemaArchivos;
import avilaos.archivos.TipoNodo;
import avilaos.disco.Disco;
import avilaos.disco.OperacionES;
import avilaos.disco.OyenteDisco;
import avilaos.disco.SolicitudES;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.Proceso;
import avilaos.nucleo.programas.ProgramaArchivo;

/**
 * La parte del sistema operativo que habla con el disco: convierte la operación de un proceso de E/S en una solicitud
 * (validando permisos y reservando bloques) y, cuando el disco la termina, aplica su efecto en el sistema de archivos y
 * despierta al proceso. Es el puente entre el computador (su hilo) y el disco (el suyo): lo que debe hacerse en el
 * computador lo deja como tarea en su buzón.
 */
public class ControladorDisco implements OyenteDisco {

    /** Lo que hace falta recordar de una solicitud de archivo hasta que el disco la termina. */
    private record Contexto(Proceso proceso, ProgramaArchivo programa, NodoArchivo pendiente) {
    }

    /** Computador cuyos procesos hacen las solicitudes. */
    private final Computador computador;
    /** Disco al que se envían. */
    private final Disco disco;
    /** Sistema de archivos sobre el que operan. */
    private final SistemaArchivos archivos;

    /** Conecta el controlador con el computador, su disco y su sistema de archivos, y se registra como oyente del disco. */
    public ControladorDisco(Computador computador, Disco disco, SistemaArchivos archivos) {
        this.computador = computador;
        this.disco = disco;
        this.archivos = archivos;
        disco.setOyente(this);
    }

    public SistemaArchivos getArchivos() {
        return archivos;
    }

    /** Sesión con la que actúa el proceso: la del usuario que lo pidió (el administrador si no tiene usuario). */
    private static Sesion sesionDe(Proceso proceso) {
        String usuario = proceso.getUsuario();
        return (usuario == null || usuario.equals(Sesion.ADMIN)) ? Sesion.administrador() : Sesion.usuario(usuario);
    }

    /**
     * Genera la solicitud de disco del proceso (se llama desde el hilo del computador, cuando el proceso ejecuta su
     * instrucción de E/S). Devuelve true si la solicitud quedó en la cola; false si se rechazó (el motivo queda en el log).
     */
    public boolean pedir(Proceso proceso, ProgramaArchivo programa) {
        OperacionES operacion = proceso.getOperacionES();
        String ruta = proceso.getRutaArchivo();
        Sesion sesion = sesionDe(proceso);
        try {
            NodoArchivo pendiente = null;
            int bloque;
            int cantidad;
            switch (operacion) {
                case CREAR -> {
                    String limpia = SistemaArchivos.normalizar(ruta);
                    int corte = limpia.lastIndexOf('/');
                    NodoArchivo padre = archivos.buscar(corte == 0 ? "/" : limpia.substring(0, corte));
                    String rechazo = ControlAcceso.motivoDeRechazo(sesion, operacion, padre, false);
                    if (padre != null && rechazo != null) {
                        return rechazar(proceso, rechazo);
                    }
                    boolean archivo = programa.getTipoNuevo() == TipoNodo.ARCHIVO;
                    pendiente = archivos.reservar(limpia, programa.getTipoNuevo(), archivo ? programa.getBloquesNuevo() : 0,
                            sesion.usuario(), programa.getPermisoNuevo(), proceso.getEtiqueta());
                    programa.setPendiente(pendiente);
                    bloque = archivo ? pendiente.getPrimerBloque() : 0;
                    cantidad = archivo ? programa.getBloquesNuevo() : 1;
                }
                case LEER, ACTUALIZAR, ELIMINAR -> {
                    NodoArchivo objetivo = archivos.buscar(ruta);
                    if (objetivo == null) {
                        return rechazar(proceso, "no existe " + SistemaArchivos.normalizar(ruta));
                    }
                    boolean renombra = operacion == OperacionES.ACTUALIZAR && programa.getNuevoNombre() != null;
                    if (renombra) {
                        SistemaArchivos.validarNombre(programa.getNuevoNombre());
                    }
                    String rechazo = ControlAcceso.motivoDeRechazo(sesion, operacion, objetivo, renombra);
                    if (rechazo != null) {
                        return rechazar(proceso, rechazo);
                    }
                    bloque = archivos.bloqueDeReferencia(objetivo);
                    cantidad = archivos.bloquesATransferir(objetivo, operacion, renombra);
                }
                default -> {
                    return rechazar(proceso, "la operación " + operacion + " no es de archivos");
                }
            }
            SolicitudES solicitud = new SolicitudES(operacion, bloque, cantidad, proceso.getId(), proceso.getEtiqueta(),
                    ruta, computador.getCicloActual());
            solicitud.setContexto(new Contexto(proceso, programa, pendiente));
            disco.encolar(solicitud);
            computador.registrarEvento(proceso.getEtiqueta() + " pide al disco " + operacion + " " + ruta
                    + " (bloque " + bloque + ")");
            return true;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return rechazar(proceso, e.getMessage());
        }
    }

    private boolean rechazar(Proceso proceso, String motivo) {
        computador.registrarEvento(proceso.getEtiqueta() + " no puede hacer " + proceso.getOperacionES() + " "
                + proceso.getRutaArchivo() + ": " + motivo);
        return false;
    }

    /** El disco terminó la solicitud (se llama desde el hilo del disco): aplica su efecto y avisa al computador. */
    @Override
    public void solicitudAtendida(SolicitudES solicitud, int ciclo) {
        Object contexto = solicitud.getContexto();
        if (contexto instanceof Contexto c) {
            terminarArchivo(solicitud, c);
        } else if (contexto instanceof Proceso proceso) {
            PlanificadorMedianoPlazo mediano = computador.getMedianoPlazo();
            if (solicitud.getOperacion() == OperacionES.SUSPENDER) {
                computador.dejarTarea(() -> mediano.alCompletarSuspension(proceso));
            } else {
                computador.dejarTarea(() -> mediano.alCompletarReactivacion(proceso));
            }
        }
    }

    private void terminarArchivo(SolicitudES solicitud, Contexto c) {
        Proceso proceso = c.proceso();
        if (proceso.getEstado() == EstadoProceso.TERMINADO) {
            if (c.pendiente() != null) {
                archivos.cancelarCreacion(c.pendiente());
            }
            computador.registrarEvento("La solicitud " + solicitud.getOperacion() + " de " + proceso.getEtiqueta()
                    + " se descarta: el proceso ya terminó");
            return;
        }
        String ruta = solicitud.getRuta();
        try {
            switch (solicitud.getOperacion()) {
                case CREAR -> {
                    archivos.confirmarCreacion(c.pendiente());
                    computador.registrarEvento(proceso.getEtiqueta() + " crea " + ruta + " ("
                            + c.pendiente().getBloques() + " bloques desde el " + c.pendiente().getPrimerBloque() + ")");
                }
                case LEER -> computador.registrarEvento(proceso.getEtiqueta() + " termina de leer " + ruta);
                case ACTUALIZAR -> {
                    if (c.programa().getNuevoNombre() != null) {
                        archivos.renombrar(ruta, c.programa().getNuevoNombre());
                        computador.registrarEvento(proceso.getEtiqueta() + " renombra " + ruta + " a " + c.programa().getNuevoNombre());
                    } else {
                        computador.registrarEvento(proceso.getEtiqueta() + " termina de actualizar " + ruta);
                    }
                }
                case ELIMINAR -> {
                    int liberados = archivos.eliminar(ruta);
                    computador.registrarEvento(proceso.getEtiqueta() + " elimina " + ruta + " y libera " + liberados + " bloques");
                }
                default -> {
                }
            }
        } catch (IllegalArgumentException e) {
            computador.registrarEvento(proceso.getEtiqueta() + " no pudo completar " + solicitud.getOperacion() + " " + ruta
                    + ": " + e.getMessage());
        }
        computador.dejarTarea(() -> computador.despertar(proceso));
    }
}

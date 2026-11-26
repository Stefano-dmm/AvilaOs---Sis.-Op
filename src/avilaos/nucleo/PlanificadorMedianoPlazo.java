package avilaos.nucleo;

import avilaos.contratos.CriterioSuspension;
import avilaos.disco.Disco;
import avilaos.disco.OperacionES;
import avilaos.disco.SolicitudES;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.Proceso;

/**
 * Planificador a mediano plazo del computador. Cuando un proceso Nuevo no cabe en la RAM elige un proceso Bloqueado
 * (según un CriterioSuspension) y lo suspende: escribe su memoria en el área de intercambio del disco y libera la RAM.
 * Cuando hay RAM libre y ningún proceso nuevo espera, reactiva a un proceso Listo/Suspendido trayendo su memoria
 * del swap. Suspender y reactivar son solicitudes de E/S que pasan por la cola del disco como cualquier otra.
 *
 * <p>Corre dentro del hilo del computador (lo llama onTick y terminarProceso). Lo que el disco termina llega como tarea
 * en el buzón del computador (alCompletarSuspension, alCompletarReactivacion), así que nunca toca las colas desde otro hilo.
 */
public class PlanificadorMedianoPlazo {

    /** Computador al que sirve. */
    private final Computador computador;
    /** Disco por el que pasan las solicitudes de suspensión y reactivación. */
    private final Disco disco;
    /** Decide qué proceso bloqueado se suspende. */
    private final CriterioSuspension criterio;

    /** Crea el planificador de un computador con su disco y su criterio de suspensión. */
    public PlanificadorMedianoPlazo(Computador computador, Disco disco, CriterioSuspension criterio) {
        this.computador = computador;
        this.disco = disco;
        this.criterio = criterio;
    }

    /** Revisa si hay que suspender para admitir a un proceso nuevo, o reactivar a uno suspendido. */
    public void revisar() {
        suspenderParaAdmitir();
        reactivarSiHayLugar();
    }

    // ---- suspender ----

    /**
     * Si el primer proceso de Nuevos no cabe, suspende bloqueados hasta que la RAM libre más la que ya está en camino
     * (suspensiones en curso) alcance. Si no hay candidatos o no caben en el swap, el proceso sigue esperando en Nuevos.
     */
    private void suspenderParaAdmitir() {
        Proceso espera = computador.getColaNuevos().frente();
        if (espera == null || espera.getMemoriaRequerida() > computador.getRamTotal()) {
            return;
        }
        int faltante = espera.getMemoriaRequerida() - computador.getRamLibre() - memoriaEnCamino();
        ListaEnlazada<Proceso> descartados = new ListaEnlazada<>();
        while (faltante > 0) {
            Proceso victima = criterio.elegir(candidatos(descartados), computador.getCicloActual());
            if (victima == null) {
                return;
            }
            if (iniciarSuspension(victima, espera)) {
                faltante -= victima.getMemoriaRequerida();
            } else {
                descartados.agregarAlFinal(victima);
            }
        }
    }

    /** Memoria que ya se está liberando: la de los procesos cuya escritura al swap está en la cola del disco. */
    private int memoriaEnCamino() {
        int total = 0;
        Nodo<Proceso> actual = computador.getColaBloqueados().getLista().getCabeza();
        while (actual != null) {
            if (actual.getDato().isSuspendiendo()) {
                total += actual.getDato().getMemoriaRequerida();
            }
            actual = actual.getSiguiente();
        }
        return total;
    }

    /** Bloqueados con memoria en la RAM que no se están suspendiendo, menos los que ya se descartaron por no caber en el swap. */
    private ListaEnlazada<Proceso> candidatos(ListaEnlazada<Proceso> descartados) {
        ListaEnlazada<Proceso> lista = new ListaEnlazada<>();
        Nodo<Proceso> actual = computador.getColaBloqueados().getLista().getCabeza();
        while (actual != null) {
            Proceso p = actual.getDato();
            if (!p.isSuspendiendo() && !p.isEventoPendiente() && p.getMemoriaRequerida() > 0 && !descartados.contiene(p)) {
                lista.agregarAlFinal(p);
            }
            actual = actual.getSiguiente();
        }
        return lista;
    }

    /** Reserva el swap del proceso y pone su escritura en la cola del disco; false si el swap no tiene espacio. */
    private boolean iniciarSuspension(Proceso victima, Proceso espera) {
        int bloques = victima.getMemoriaRequerida();
        int primero = disco.reservarBloquesSwap(bloques, victima.getId());
        if (primero < 0) {
            computador.registrarEvento("No hay espacio en el área de intercambio para suspender a " + victima.getEtiqueta()
                    + " (necesita " + bloques + " bloques, libres " + disco.getBloquesLibresSwap() + ")");
            return false;
        }
        victima.setBloqueSwap(primero);
        victima.setSuspendiendo(true);
        SolicitudES solicitud = new SolicitudES(OperacionES.SUSPENDER, primero, bloques, victima.getId(),
                victima.getEtiqueta(), null, computador.getCicloActual());
        solicitud.setContexto(victima);
        disco.encolar(solicitud);
        computador.registrarEvento("Se suspende al proceso " + victima.getEtiqueta() + " para admitir a " + espera.getEtiqueta());
        return true;
    }

    /** El disco terminó de escribir la memoria del proceso en el swap: libera su RAM y lo deja en la cola de suspendidos. */
    public void alCompletarSuspension(Proceso proceso) {
        if (proceso.getEstado() == EstadoProceso.TERMINADO || !proceso.isSuspendiendo()) {
            return;
        }
        proceso.setSuspendiendo(false);
        computador.getColaBloqueados().getLista().eliminar(proceso);
        computador.getRam().liberarMemoria(proceso);
        computador.contarSuspension();
        computador.registrarEvento(proceso.getEtiqueta() + " queda en el área de intercambio (bloque " + proceso.getBloqueSwap()
                + ", " + proceso.getMemoriaRequerida() + " bloques) y libera su RAM en " + computador.getNombre());
        if (proceso.isEventoPendiente()) {
            computador.pasarAListosSuspendidos(proceso);
        } else {
            proceso.setEstado(EstadoProceso.BLOQUEADO_SUSPENDIDO);
            computador.getColaBloqueadosSuspendidos().encolar(proceso);
        }
        computador.intentarAdmitirNuevos();
    }

    // ---- reactivar ----

    /** Con la cola de nuevos vacía, trae de vuelta a los Listo/Suspendido que caben, empezando por el de mayor prioridad. */
    private void reactivarSiHayLugar() {
        while (computador.getColaNuevos().estaVacia()) {
            Proceso elegido = masPrioritarioQueCabe();
            if (elegido == null || !computador.getRam().asignarMemoria(elegido)) {
                return;
            }
            elegido.setReactivando(true);
            SolicitudES solicitud = new SolicitudES(OperacionES.REACTIVAR, elegido.getBloqueSwap(),
                    elegido.getMemoriaRequerida(), elegido.getId(), elegido.getEtiqueta(), null, computador.getCicloActual());
            solicitud.setContexto(elegido);
            disco.encolar(solicitud);
            computador.registrarEvento("Se reactiva al proceso " + elegido.getEtiqueta() + ": trae su memoria del área de intercambio");
        }
    }

    /** El Listo/Suspendido de mayor prioridad (con empate, el que lleva más tiempo en la cola) cuya memoria cabe en la RAM libre. */
    private Proceso masPrioritarioQueCabe() {
        Proceso elegido = null;
        Nodo<Proceso> actual = computador.getColaListosSuspendidos().getLista().getCabeza();
        while (actual != null) {
            Proceso p = actual.getDato();
            if (!p.isReactivando() && p.getMemoriaRequerida() <= computador.getRamLibre()
                    && (elegido == null || p.getPrioridad() > elegido.getPrioridad())) {
                elegido = p;
            }
            actual = actual.getSiguiente();
        }
        return elegido;
    }

    /** El disco terminó de leer la memoria del proceso del swap: libera esos bloques y el proceso pasa a Listos. */
    public void alCompletarReactivacion(Proceso proceso) {
        if (proceso.getEstado() == EstadoProceso.TERMINADO || !proceso.isReactivando()) {
            return;
        }
        proceso.setReactivando(false);
        disco.liberarBloques(proceso.getBloqueSwap());
        proceso.setBloqueSwap(-1);
        computador.getColaListosSuspendidos().getLista().eliminar(proceso);
        computador.contarReactivacion();
        computador.registrarEvento(proceso.getEtiqueta() + " vuelve a la RAM de " + computador.getNombre() + " y pasa a Listos");
        computador.pasarAListosReactivado(proceso);
    }
}

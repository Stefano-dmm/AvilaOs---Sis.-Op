/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.memoria;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.RAM;
import avilaos.modelo.Proceso;

/**
 * Coordina el intercambio de procesos entre la memoria principal ({@link RAM}) y el
 * almacenamiento secundario ({@link DiscoSecundario}), manteniendo el registro de
 * procesos suspendidos mediante estructuras propias.
 *
 * @author bethe
 */
public class GestorMemoriaVirtual {

    private final RAM ram;
    private final DiscoSecundario disco;
    private final ListaEnlazada<Proceso> procesosSuspendidos;

    /**
     * Inicializa el gestor de memoria virtual con su RAM y disco secundario asociados.
     *
     * @param ram instancia de memoria principal
     * @param disco instancia de almacenamiento secundario
     * @throws IllegalArgumentException si {@code ram} o {@code disco} son nulos
     */
    public GestorMemoriaVirtual(RAM ram, DiscoSecundario disco) {
        if (ram == null) {
            throw new IllegalArgumentException("La memoria RAM no puede ser nula.");
        }
        if (disco == null) {
            throw new IllegalArgumentException("El disco secundario no puede ser nulo.");
        }
        this.ram = ram;
        this.disco = disco;
        this.procesosSuspendidos = new ListaEnlazada<>();
    }

    /**
     * Intenta cargar o devolver un proceso desde disco hacia la RAM si hay espacio disponible.
     * Si la operación tiene éxito, retira el proceso del disco y de la lista de suspendidos.
     *
     * @param proceso proceso a mover a memoria principal
     * @return {@code true} si el proceso quedó asignado en RAM; {@code false} si no hubo espacio
     */
    public boolean moverARam(Proceso proceso) {
        if (proceso == null) {
            return false;
        }
        int requerida = proceso.getMemoriaRequerida();
        if (ram.getMemoriaDisponible() < requerida) {
            return false;
        }
        if (!ram.asignarMemoria(proceso)) {
            return false;
        }
        if (disco.contieneProceso(proceso)) {
            disco.liberarProceso(proceso);
        }
        procesosSuspendidos.eliminar(proceso);
        return true;
    }

    /**
     * Respalda un proceso en disco, libera su espacio en RAM y lo registra como suspendido.
     *
     * @param proceso proceso a suspender
     * @return {@code true} si el proceso quedó respaldado y suspendido; {@code false} si falló
     */
    public boolean suspenderProceso(Proceso proceso) {
        if (proceso == null) {
            return false;
        }
        if (disco.contieneProceso(proceso)) {
            return true;
        }
        if (!disco.guardarProceso(proceso)) {
            return false;
        }
        ram.liberarMemoria(proceso);
        if (!procesosSuspendidos.contiene(proceso)) {
            procesosSuspendidos.agregarAlFinal(proceso);
        }
        return true;
    }

    /**
     * Reanuda un proceso previamente respaldado en disco moviéndolo de vuelta a la RAM.
     *
     * @param proceso proceso suspendido a reanudar
     * @return {@code true} si logró moverse a RAM; {@code false} si no está en disco o falta RAM
     */
    public boolean reanudarProceso(Proceso proceso) {
        if (proceso == null || !disco.contieneProceso(proceso)) {
            return false;
        }
        return moverARam(proceso);
    }

    /**
     * Busca un proceso en la lista de procesos suspendidos a partir de su identificador.
     *
     * @param id identificador del proceso buscado
     * @return el {@link Proceso} encontrado o {@code null} si no existe en suspendidos
     */
    public Proceso buscarProcesoSuspendidoPorId(int id) {
        Nodo<Proceso> actual = procesosSuspendidos.getCabeza();
        while (actual != null) {
            Proceso p = actual.getDato();
            if (p != null && p.getId() == id) {
                return p;
            }
            actual = actual.getSiguiente();
        }
        return null;
    }

    /** @return memoria principal administrada por el gestor. */
    public RAM getRam() {
        return ram;
    }

    /** @return disco secundario administrado por el gestor. */
    public DiscoSecundario getDisco() {
        return disco;
    }

    /** @return lista enlazada de procesos actualmente suspendidos. */
    public ListaEnlazada<Proceso> getProcesosSuspendidos() {
        return procesosSuspendidos;
    }
}
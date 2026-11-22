/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.memoria;

/**
 *
 * @author bethe
 */

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.RAM;
import avilaos.modelo.Proceso;

public class GestorMemoriaVirtual {

    private final RAM ram;
    private final DiscoSecundario disco;
    private final ListaEnlazada<Proceso> procesosSuspendidos;

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

    public boolean reanudarProceso(Proceso proceso) {
        if (proceso == null || !disco.contieneProceso(proceso)) {
            return false;
        }
        return moverARam(proceso);
    }

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

    public RAM getRam() {
        return ram;
    }

    public DiscoSecundario getDisco() {
        return disco;
    }

    public ListaEnlazada<Proceso> getProcesosSuspendidos() {
        return procesosSuspendidos;
    }
}
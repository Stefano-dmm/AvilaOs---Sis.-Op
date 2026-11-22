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
import avilaos.modelo.Proceso;

public class DiscoSecundario {

    private final int capacidadTotal;
    private int espacioUsado;
    private final ListaEnlazada<Proceso> procesosEnDisco;

    public DiscoSecundario(int capacidadTotal) {
        if (capacidadTotal <= 0) {
            throw new IllegalArgumentException("La capacidad del disco debe ser mayor que cero.");
        }
        this.capacidadTotal = capacidadTotal;
        this.espacioUsado = 0;
        this.procesosEnDisco = new ListaEnlazada<>();
    }

    public boolean tieneEspacio(int memoriaRequerida) {
        if (memoriaRequerida < 0) {
            return false;
        }
        return espacioUsado + memoriaRequerida <= capacidadTotal;
    }

    public boolean guardarProceso(Proceso proceso) {
        if (proceso == null || procesosEnDisco.contiene(proceso)) {
            return false;
        }
        int requerido = proceso.getMemoriaRequerida();
        if (!tieneEspacio(requerido)) {
            return false;
        }
        procesosEnDisco.agregarAlFinal(proceso);
        espacioUsado += requerido;
        return true;
    }

    public boolean contieneProceso(Proceso proceso) {
        return proceso != null && procesosEnDisco.contiene(proceso);
    }

    public boolean liberarProceso(Proceso proceso) {
        if (proceso == null) {
            return false;
        }
        if (procesosEnDisco.eliminar(proceso)) {
            espacioUsado -= proceso.getMemoriaRequerida();
            if (espacioUsado < 0) {
                espacioUsado = 0;
            }
            return true;
        }
        return false;
    }

    public int getCapacidadTotal() {
        return capacidadTotal;
    }

    public int getEspacioUsado() {
        return espacioUsado;
    }

    public int getEspacioDisponible() {
        return capacidadTotal - espacioUsado;
    }

    public ListaEnlazada<Proceso> getProcesosEnDisco() {
        return procesosEnDisco;
    }
}
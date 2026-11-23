/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

import avilaos.estructuras.ListaEnlazada;
import avilaos.modelo.Proceso;

/**
 *
 * @author bethe
 */
public class RAM {

    private int capacidadTotal; // Capacidad máxima de la RAM (ej. en MB o unidades)[cite: 48]
    private int memoriaUsada;   // Memoria actualmente ocupada[cite: 48]
    private final ListaEnlazada<Proceso> procesosEnMemoria; // Procesos cargados en RAM[cite: 48]

    public RAM(int capacidadTotal) {
        this.capacidadTotal = capacidadTotal;
        this.memoriaUsada = 0;
        this.procesosEnMemoria = new ListaEnlazada<>();
    }

    public boolean asignarMemoria(Proceso proceso) {
        if (proceso == null) {
            return false;
        }
        int tamanoProceso = proceso.getMemoriaRequerida();
        if (memoriaUsada + tamanoProceso <= capacidadTotal) {
            procesosEnMemoria.agregarAlFinal(proceso);
            memoriaUsada += tamanoProceso;
            return true;
        }
        return false; // No hay espacio suficiente en RAM[cite: 48]
    }

    /**
     * Libera la memoria ocupada por un proceso que ha finalizado o fue suspendido.[cite: 48]
     */
    public void liberarMemoria(Proceso proceso) {
        if (proceso == null) {
            return;
        }
        if (procesosEnMemoria.eliminar(proceso)) {
            memoriaUsada -= proceso.getMemoriaRequerida();
            if (memoriaUsada < 0) {
                memoriaUsada = 0;
            }
        }
    }

    public int getMemoriaDisponible() {
        return capacidadTotal - memoriaUsada;
    }

    // Getters y Setters[cite: 49]
    public int getCapacidadTotal() {
        return capacidadTotal;
    }

    public void setCapacidadTotal(int capacidadTotal) {
        this.capacidadTotal = capacidadTotal;
    }

    public int getMemoriaUsada() {
        return memoriaUsada;
    }

    public ListaEnlazada<Proceso> getProcesosEnMemoria() {
        return procesosEnMemoria;
    }
}
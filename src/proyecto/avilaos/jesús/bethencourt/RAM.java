/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package proyecto.avilaos.jesús.bethencourt;

import avilaos.modelo.Proceso;
import java.util.ArrayList;
  /**
 *
 * @author bethe
 */
public class RAM {
    private int capacidadTotal;       // Capacidad máxima de la RAM (ej. en MB o unidades)
    private int memoriaUsada;         // Memoria actualmente ocupada
    private ArrayList<Proceso> procesosEnMemoria; // Procesos cargados en RAM

    public RAM(int capacidadTotal) {
        this.capacidadTotal = capacidadTotal;
        this.memoriaUsada = 0;
        this.procesosEnMemoria = new ArrayList<>();
    }

    public boolean asignarMemoria(Proceso proceso) {
        int tamanoProceso = proceso.getTamano();
        
        if (memoriaUsada + tamanoProceso <= capacidadTotal) {
            procesosEnMemoria.add(proceso);
            memoriaUsada += tamanoProceso;
            return true;
        }
        return false; // No hay espacio suficiente en RAM
    }

    /**
     * Libera la memoria ocupada por un proceso que ha finalizado.
     */
    public void liberarMemoria(Proceso proceso) {
        if (procesosEnMemoria.remove(proceso)) {
            memoriaUsada -= proceso.getTamano();
        }
    }

    public int getMemoriaDisponible() {
        return capacidadTotal - memoriaUsada;
    }

    // Getters y Setters

    public int getCapacidadTotal() {
        return capacidadTotal;
    }

    public void setCapacidadTotal(int capacidadTotal) {
        this.capacidadTotal = capacidadTotal;
    }

    public int getMemoriaUsada() {
        return memoriaUsada;
    }

    public ArrayList<Proceso> getProcesosEnMemoria() {
        return procesosEnMemoria;
    }
}
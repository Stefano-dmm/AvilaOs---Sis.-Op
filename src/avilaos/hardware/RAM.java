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
    private int capacidadTotal;       // Capacidad máxima de la RAM (ej. en MB o unidades)
    private int memoriaUsada;         // Memoria actualmente ocupada
    private final ListaEnlazada<Proceso> procesosEnMemoria; // Procesos cargados en RAM

    /** Crea una RAM vacia con la capacidad dada. */
    public RAM(int capacidadTotal) {
        this.capacidadTotal = capacidadTotal;
        this.memoriaUsada = 0;
        this.procesosEnMemoria = new ListaEnlazada<>();
    }

    /** Reserva la memoria que pide un proceso y lo anota en la lista de procesos en memoria; devuelve false si no cabe (el proceso sigue en Nuevos). */
    public boolean asignarMemoria(Proceso proceso) {
        int tamanoProceso = proceso.getMemoriaRequerida();

        if (memoriaUsada + tamanoProceso <= capacidadTotal) {
            procesosEnMemoria.agregarAlFinal(proceso);
            memoriaUsada += tamanoProceso;
            return true;
        }
        return false; // No hay espacio suficiente en RAM
    }

    /**
     * Libera la memoria ocupada por un proceso que ha finalizado.
     */
    public void liberarMemoria(Proceso proceso) {
        if (procesosEnMemoria.eliminar(proceso)) {
            memoriaUsada -= proceso.getMemoriaRequerida();
        }
    }

    /** Reserva memoria que no pertenece a un proceso (por ejemplo un buffer); false si no cabe. */
    public boolean reservar(int cantidad) {
        if (cantidad < 0 || cantidad > getMemoriaDisponible()) {
            return false;
        }
        memoriaUsada += cantidad;
        return true;
    }

    /** Devuelve memoria reservada con reservar (nunca baja de 0). */
    public void liberar(int cantidad) {
        memoriaUsada = Math.max(0, memoriaUsada - cantidad);
    }

    /** Memoria libre: capacidad total menos la usada. */
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

    /** Procesos que tienen RAM reservada. No se modifica desde fuera: solo la RAM cambia esta lista. */
    public ListaEnlazada<Proceso> getProcesosEnMemoria() {
        return procesosEnMemoria;
    }
}

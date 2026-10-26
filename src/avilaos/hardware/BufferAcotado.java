/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

/**
 *
 * @author bethe
 */
public class BufferAcotado<T> {
    private T[] buffer;
    private int capacidad;
    private int cabeza;
    private int cola;
    private int elementosActuales;

    @SuppressWarnings("unchecked")
    public BufferAcotado(int capacidad) {
        this.capacidad = capacidad;
        // Usamos un arreglo nativo para cumplir con la restricción de estructuras propias
        this.buffer = (T[]) new Object[capacidad];
        this.cabeza = 0;
        this.cola = 0;
        this.elementosActuales = 0;
    }

    /**
     * Inserta un elemento en el búfer. Si está lleno, el hilo productor espera.
     */
    public synchronized void producir(T item) throws InterruptedException {
        while (elementosActuales == capacidad) {
            wait(); // El búfer está lleno, esperamos espacio
        }
        
        buffer[cola] = item;
        cola = (cola + 1) % capacidad; // Puntero circular
        elementosActuales++;
        
        notifyAll(); // Avisamos a los consumidores que hay nuevos elementos
    }

    /**
     * Extrae un elemento del búfer. Si está vacío, el hilo consumidor espera.
     */
    public synchronized T consumir() throws InterruptedException {
        while (elementosActuales == 0) {
            wait(); // El búfer está vacío, esperamos elementos
        }
        
        T item = buffer[cabeza];
        buffer[cabeza] = null; // Limpiamos la referencia para el recolector de basura
        cabeza = (cabeza + 1) % capacidad; // Puntero circular
        elementosActuales--;
        
        notifyAll(); // Avisamos a los productores que hay espacio libre
        return item;
    }

    public synchronized boolean estaVacio() {
        return elementosActuales == 0;
    }

    public synchronized boolean estaLleno() {
        return elementosActuales == capacidad;
    }

    public int getElementosActuales() {
        return elementosActuales;
    }

    public int getCapacidad() {
        return capacidad;
    }
}
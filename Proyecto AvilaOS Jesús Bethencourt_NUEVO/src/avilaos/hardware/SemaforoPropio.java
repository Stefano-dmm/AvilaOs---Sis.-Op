/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

/**
 *
 * @author bethe
 */
public class SemaforoPropio {
    private int valor;
    private NodoHilo frente;
    private NodoHilo fin;
    private static class NodoHilo {
        Thread hilo;
        NodoHilo siguiente;

        public NodoHilo(Thread hilo) {
            this.hilo = hilo;
            this.siguiente = null;
        }
    }

    public SemaforoPropio(int valorInicial) {
        this.valor = valorInicial;
        this.frente = null;
        this.fin = null;
    }

    /**
     * Operación P (Wait / Adquirir). 
     * Decrementa el valor del semáforo y bloquea el hilo si es necesario.
     */
    public synchronized void adquirir() throws InterruptedException {
        valor--;
        if (valor < 0) {
            // El recurso no está disponible, encolamos el hilo actual de forma manual
            NodoHilo nuevoNodo = new NodoHilo(Thread.currentThread());
            if (fin == null) {
                frente = nuevoNodo;
                fin = nuevoNodo;
            } else {
                fin.siguiente = nuevoNodo;
                fin = nuevoNodo;
            }
            wait(); // El hilo entra en espera
        }
    }

    /**
     * Operación V (Signal / Liberar). 
     * Incrementa el valor del semáforo y despierta al siguiente hilo en espera.
     */
    public synchronized void liberar() {
        valor++;
        if (valor <= 0) {
            // Sacamos al primer hilo de nuestra cola propia
            if (frente != null) {
                NodoHilo nodoADespertar = frente;
                frente = frente.siguiente;
                if (frente == null) {
                    fin = null; // La cola quedó vacía
                }
                notify(); // Despierta a uno de los hilos en espera
            }
        }
    }

    public synchronized int getValor() {
        return valor;
    }
}
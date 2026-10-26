/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

/**
 *
 * @author bethe
 */
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;

public class RelojGlobal implements Runnable {
    private long cicloActual;
    private boolean activo;
    private long velocidadMs; // Tiempo en milisegundos por cada tic del reloj
    private final ListaEnlazada<Clockable> suscriptores;

    public RelojGlobal(long velocidadMs) {
        this.cicloActual = 0;
        this.activo = false;
        this.velocidadMs = velocidadMs;
        this.suscriptores = new ListaEnlazada<>();
    }

    public void suscribir(Clockable c) {
        if (!suscriptores.contiene(c)) {
            suscriptores.agregarAlFinal(c);
        }
    }

    public void desuscribir(Clockable c) {
        suscriptores.eliminar(c);
    }

    private void notificarSuscriptores() {
        // Recorremos los suscriptores para avisarles que ocurrió un nuevo ciclo
        Nodo<Clockable> actual = suscriptores.getCabeza();
        while (actual != null) {
            actual.getDato().tic(cicloActual);
            actual = actual.getSiguiente();
        }
    }

    @Override
    public void run() {
        activo = true;
        while (activo) {
            try {
                Thread.sleep(velocidadMs);
                cicloActual++;
                notificarSuscriptores();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void detener() {
        this.activo = false;
    }

    //Getters y Setters

    public long getCicloActual() {
        return cicloActual;
    }

    public void setVelocidadMs(long velocidadMs) {
        this.velocidadMs = velocidadMs;
    }

    public boolean isActivo() {
        return activo;
    }
}

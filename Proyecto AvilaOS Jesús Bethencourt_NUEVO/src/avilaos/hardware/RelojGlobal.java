/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;

/**
 *
 * @author bethe
 */
public class RelojGlobal implements Runnable {

    private long cicloActual;
    private boolean activo;
    private long velocidadMs; // Tiempo en milisegundos por cada tic del reloj[cite: 50]
    private final ListaEnlazada<Clockable> suscriptores;

    public RelojGlobal(long velocidadMs) {
        this.cicloActual = 0;
        this.activo = false;
        this.velocidadMs = velocidadMs;
        this.suscriptores = new ListaEnlazada<>();
    }

    public void suscribir(Clockable c) {
        if (c == null) {
            return;
        }
        synchronized (suscriptores) {
            if (!suscriptores.contiene(c)) {
                suscriptores.agregarAlFinal(c);
            }
        }
    }

    public void desuscribir(Clockable c) {
        if (c == null) {
            return;
        }
        synchronized (suscriptores) {
            suscriptores.eliminar(c);
        }
    }

    private void notificarSuscriptores() {
        synchronized (suscriptores) {
            Nodo<Clockable> actual = suscriptores.getCabeza();
            while (actual != null) {
                Clockable suscriptor = actual.getDato();
                if (suscriptor != null) {
                    suscriptor.tic(cicloActual);
                }
                actual = actual.getSiguiente();
            }
        }
    }

    @Override
    public void run() {
        synchronized (this) {
            activo = true;
        }
        while (isActivo()) {
            try {
                Thread.sleep(velocidadMs);
                long cicloAEnviar;
                synchronized (this) {
                    if (!activo) {
                        break;
                    }
                    cicloActual++;
                    cicloAEnviar = cicloActual;
                }
                notificarSuscriptoresConCiclo(cicloAEnviar);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void notificarSuscriptoresConCiclo(long ciclo) {
        synchronized (suscriptores) {
            Nodo<Clockable> actual = suscriptores.getCabeza();
            while (actual != null) {
                Clockable suscriptor = actual.getDato();
                if (suscriptor != null) {
                    suscriptor.tic(ciclo);
                }
                actual = actual.getSiguiente();
            }
        }
    }

    public void detener() {
        synchronized (this) {
            this.activo = false;
        }
    }

    // Getters y Setters[cite: 51]
    public synchronized long getCicloActual() {
        return cicloActual;
    }

    public synchronized void setVelocidadMs(long velocidadMs) {
        this.velocidadMs = velocidadMs;
    }

    public synchronized boolean isActivo() {
        return activo;
    }
}

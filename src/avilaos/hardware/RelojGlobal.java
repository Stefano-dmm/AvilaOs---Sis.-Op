/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * Reloj global del sistema distribuido (base: clase de B). Es un hilo que marca el ritmo: en cada
 * ciclo avisa a todos los suscriptores (los computadores) y espera a que todos terminen antes de
 * pasar al siguiente, asi cada computador avanza exactamente un ciclo por tic. Cada suscriptor corre
 * en su propio hilo, de modo que los computadores trabajan en paralelo.
 *
 * @author bethe
 */
public class RelojGlobal implements Runnable {

    /** Un computador suscrito: su hilo espera el tic, lo ejecuta y avisa que termino. */
    private static final class Suscriptor {

        private final Clockable clockable;
        private final Semaphore inicio = new Semaphore(0);
        private final Semaphore fin = new Semaphore(0);
        private volatile long ciclo;
        private volatile boolean cerrado;
        private volatile Throwable error;

        Suscriptor(Clockable clockable) {
            this.clockable = clockable;
            Thread hilo = new Thread(this::bucle, "suscriptor-reloj");
            hilo.setDaemon(true);
            hilo.start();
        }

        private void bucle() {
            while (true) {
                inicio.acquireUninterruptibly();
                if (cerrado) {
                    return;
                }
                try {
                    clockable.tic(ciclo);
                } catch (RuntimeException ex) {
                    error = ex;
                }
                fin.release();
            }
        }

        void cerrar() {
            cerrado = true;
            inicio.release();
        }
    }

    private volatile long cicloActual;
    private volatile boolean activo;
    private volatile boolean enMarcha;
    private volatile long velocidadMs; // Tiempo en milisegundos por cada tic del reloj
    private final ListaEnlazada<Suscriptor> suscriptores = new ListaEnlazada<>();
    private final Semaphore candadoSuscriptores = new Semaphore(1);
    private final Semaphore pasos = new Semaphore(0);
    private Semaphore cerrojo;
    private Consumer<Long> alCompletarCiclo;
    private Consumer<Throwable> alFallar;
    private Thread hilo;

    public RelojGlobal(long velocidadMs) {
        this.cicloActual = 0;
        this.activo = false;
        this.velocidadMs = velocidadMs;
    }

    public void suscribir(Clockable c) {
        candadoSuscriptores.acquireUninterruptibly();
        try {
            if (buscar(c) == null) {
                suscriptores.agregarAlFinal(new Suscriptor(c));
            }
        } finally {
            candadoSuscriptores.release();
        }
    }

    public void desuscribir(Clockable c) {
        candadoSuscriptores.acquireUninterruptibly();
        try {
            Suscriptor s = buscar(c);
            if (s != null) {
                suscriptores.eliminar(s);
                s.cerrar();
            }
        } finally {
            candadoSuscriptores.release();
        }
    }

    private Suscriptor buscar(Clockable c) {
        Nodo<Suscriptor> actual = suscriptores.getCabeza();
        while (actual != null) {
            if (actual.getDato().clockable == c) {
                return actual.getDato();
            }
            actual = actual.getSiguiente();
        }
        return null;
    }

    /** Cerrojo que se mantiene tomado durante cada ciclo, para que nadie lea o modifique a mitad de uno. */
    public void setCerrojo(Semaphore cerrojo) {
        this.cerrojo = cerrojo;
    }

    /** Se ejecuta en el hilo del reloj al terminar cada ciclo, con el cerrojo tomado. */
    public void setAlCompletarCiclo(Consumer<Long> alCompletarCiclo) {
        this.alCompletarCiclo = alCompletarCiclo;
    }

    /** Se ejecuta si un computador falla durante un ciclo; el reloj queda en pausa. */
    public void setAlFallar(Consumer<Throwable> alFallar) {
        this.alFallar = alFallar;
    }

    /** Avisa a todos los suscriptores del nuevo ciclo y espera a que todos terminen. */
    private Throwable notificarSuscriptores() {
        candadoSuscriptores.acquireUninterruptibly();
        Suscriptor[] copia;
        try {
            copia = new Suscriptor[suscriptores.getTamanio()];
            Nodo<Suscriptor> actual = suscriptores.getCabeza();
            for (int i = 0; actual != null; i++) {
                copia[i] = actual.getDato();
                actual = actual.getSiguiente();
            }
        } finally {
            candadoSuscriptores.release();
        }
        for (Suscriptor s : copia) {
            s.ciclo = cicloActual;
            s.inicio.release();
        }
        Throwable fallo = null;
        for (Suscriptor s : copia) {
            s.fin.acquireUninterruptibly();
            if (s.error != null && fallo == null) {
                fallo = s.error;
            }
            s.error = null;
        }
        return fallo;
    }

    private void ejecutarCiclo() {
        if (cerrojo != null) {
            cerrojo.acquireUninterruptibly();
        }
        Throwable fallo;
        try {
            cicloActual++;
            fallo = notificarSuscriptores();
            if (fallo == null && alCompletarCiclo != null) {
                alCompletarCiclo.accept(cicloActual);
            }
        } finally {
            if (cerrojo != null) {
                cerrojo.release();
            }
        }
        if (fallo != null) {
            pausar();
            if (alFallar != null) {
                alFallar.accept(fallo);
            }
        }
    }

    @Override
    public void run() {
        activo = true;
        while (activo) {
            try {
                if (enMarcha) {
                    ejecutarCiclo();
                    Thread.sleep(Math.max(0, velocidadMs));
                } else {
                    pasos.acquire();
                    if (!activo) {
                        break;
                    }
                    ejecutarCiclo();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private synchronized void asegurarHilo() {
        if (hilo == null) {
            activo = true;
            hilo = new Thread(this, "reloj-global");
            hilo.setDaemon(true);
            hilo.start();
        }
    }

    /** Hace que el reloj avance solo, un ciclo cada velocidadMs. */
    public void iniciar() {
        asegurarHilo();
        enMarcha = true;
        pasos.release();
    }

    /** Detiene el avance automatico; el reloj puede seguir con paso() o iniciar(). */
    public void pausar() {
        enMarcha = false;
        pasos.drainPermits();
    }

    /** Avanza un solo ciclo (solo si el reloj esta en pausa). */
    public void paso() {
        asegurarHilo();
        if (!enMarcha) {
            pasos.release();
        }
    }

    public void detener() {
        activo = false;
        enMarcha = false;
        pasos.release();
        if (hilo != null) {
            hilo.interrupt();
        }
        candadoSuscriptores.acquireUninterruptibly();
        try {
            Nodo<Suscriptor> actual = suscriptores.getCabeza();
            while (actual != null) {
                actual.getDato().cerrar();
                actual = actual.getSiguiente();
            }
        } finally {
            candadoSuscriptores.release();
        }
    }

    //Getters y Setters

    public long getCicloActual() {
        return cicloActual;
    }

    public void setVelocidadMs(long velocidadMs) {
        this.velocidadMs = velocidadMs;
    }

    public long getVelocidadMs() {
        return velocidadMs;
    }

    public boolean isActivo() {
        return activo;
    }

    public boolean isEnMarcha() {
        return enMarcha;
    }
}

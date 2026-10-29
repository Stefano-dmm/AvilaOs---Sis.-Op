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

        /** El componente (computador) que recibe los tics. */
        private final Clockable clockable;
        /**
         * Senal de INICIO de ciclo: el reloj la libera para despertar el hilo de este suscriptor, que ejecuta tic y
         * vuelve a esperar. Arranca en 0 (sin permisos).
         */
        private final Semaphore inicio = new Semaphore(0);
        /**
         * Senal de FIN de ciclo: el hilo del suscriptor la libera al terminar su tic. El reloj toma una por
         * suscriptor antes de pasar al siguiente ciclo: es la barrera que obliga a que todos avancen un ciclo exacto.
         */
        private final Semaphore fin = new Semaphore(0);
        /** Numero de ciclo que el reloj le entrega en este tic (volatile: lo escribe el hilo del reloj y lo lee el del suscriptor). */
        private volatile long ciclo;
        /** Pide al hilo del suscriptor que termine. */
        private volatile boolean cerrado;
        /** Excepcion lanzada por el tic, para que el reloj se pause y avise a la interfaz. */
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

    /** Ciclos transcurridos desde que se creo el reloj. volatile: lo lee la interfaz mientras el hilo del reloj lo escribe. */
    private volatile long cicloActual;
    /** Verdadero mientras el hilo del reloj debe seguir vivo. */
    private volatile boolean activo;
    /** Verdadero si el reloj avanza solo; falso en pausa (solo avanza con paso()). */
    private volatile boolean enMarcha;
    private volatile long velocidadMs; // Tiempo en milisegundos por cada tic del reloj
    /** Computadores suscritos. Protegida por candadoSuscriptores. */
    private final ListaEnlazada<Suscriptor> suscriptores = new ListaEnlazada<>();
    /**
     * Semaphore de Java que protege la lista suscriptores: suscribir y desuscribir pueden llamarse desde la
     * interfaz mientras el hilo del reloj la recorre para notificar.
     */
    private final Semaphore candadoSuscriptores = new Semaphore(1);
    /**
     * Semaphore de SENALIZACION (no de exclusion mutua). En pausa, el hilo del reloj se bloquea aqui; cada permiso
     * liberado por paso() o iniciar() equivale a un ciclo pendiente. pausar() descarta los permisos sobrantes.
     */
    private final Semaphore pasos = new Semaphore(0);
    /**
     * Cerrojo del estado compartido (el mismo Semaphore que Cluster.cerrojo). El reloj lo mantiene tomado durante
     * TODO el ciclo, de modo que la interfaz (que lo toma para leer o modificar) nunca ve ni cambia un ciclo a medias.
     */
    private Semaphore cerrojo;
    /** Funcion que se ejecuta en el hilo del reloj al terminar cada ciclo, con el cerrojo tomado. */
    private Consumer<Long> alCompletarCiclo;
    /** Funcion que se ejecuta si algun computador lanza una excepcion durante un ciclo. */
    private Consumer<Throwable> alFallar;
    /** Hilo del reloj; se crea la primera vez que se inicia o se da un paso. */
    private Thread hilo;

    /** Crea el reloj en pausa; el hilo se crea al iniciar o dar el primer paso. */
    public RelojGlobal(long velocidadMs) {
        this.cicloActual = 0;
        this.activo = false;
        this.velocidadMs = velocidadMs;
    }

    /** Suscribe un componente: se le crea su hilo y recibira un tic por ciclo. Si ya estaba suscrito no hace nada. */
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

    /** Quita al componente y termina su hilo. */
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

    /** Busca el suscriptor de un componente; null si no esta. Debe llamarse con candadoSuscriptores tomado. */
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

    /** Un ciclo completo: toma el cerrojo, avanza el contador, notifica a los computadores, avisa a la interfaz y suelta el cerrojo. */
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

    /** Bucle del hilo del reloj: en marcha ejecuta un ciclo y duerme velocidadMs; en pausa espera un permiso en pasos. */
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

    /** Crea el hilo del reloj la primera vez que hace falta. */
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

    /** Termina el hilo del reloj y los hilos de todos los suscriptores (al cerrar la ventana). */
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

package avilaos.pruebas;

import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.estructuras.Nodo;
import avilaos.hardware.Clockable;
import avilaos.hardware.RelojGlobal;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.Proceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.sincronizacion.BufferSO;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Pruebas del reloj global con hilos: un tic por computador, pausa, paso a paso y ausencia de carreras. */
public final class PruebasReloj {

    private static final long ESPERA_MAXIMA_MS = 15000;

    private PruebasReloj() {
    }

    public static void ejecutar() throws InterruptedException {
        unTicPorComputador();
        pausaYPasoAPaso();
        simulacionesConHilos();
        registrosDeLaCpu();
    }

    /** Un componente cualquiera que reacciona al reloj: comparte la interfaz Clockable. */
    private static final class Contador implements Clockable {

        private volatile int tics;
        private volatile long ultimoCiclo;

        @Override
        public void tic(long cicloActual) {
            tics++;
            ultimoCiclo = cicloActual;
        }
    }

    private static void unTicPorComputador() throws InterruptedException {
        seccion("Reloj global: un ciclo por computador");
        RelojGlobal reloj = new RelojGlobal(1);
        Contador a = new Contador();
        Contador b = new Contador();
        reloj.suscribir(a);
        reloj.suscribir(b);
        reloj.suscribir(a);
        Semaphore ciclo = new Semaphore(0);
        reloj.setAlCompletarCiclo(c -> ciclo.release());
        for (int i = 1; i <= 5; i++) {
            reloj.paso();
            verificar(ciclo.tryAcquire(ESPERA_MAXIMA_MS, TimeUnit.MILLISECONDS), "se completa el ciclo " + i);
        }
        igual(5, a.tics, "el primer componente recibio exactamente 5 tics");
        igual(5, b.tics, "el segundo componente recibio exactamente 5 tics");
        igual(5L, a.ultimoCiclo, "el tic lleva el numero de ciclo actual");
        reloj.desuscribir(b);
        reloj.paso();
        ciclo.tryAcquire(ESPERA_MAXIMA_MS, TimeUnit.MILLISECONDS);
        igual(6, a.tics, "el que sigue suscrito recibe el tic 6");
        igual(5, b.tics, "el que se desuscribio ya no recibe tics");
        reloj.detener();
    }

    private static void pausaYPasoAPaso() throws InterruptedException {
        seccion("Reloj global: pausa, paso a paso y marcha");
        RelojGlobal reloj = new RelojGlobal(2);
        Contador c = new Contador();
        reloj.suscribir(c);
        Semaphore ciclo = new Semaphore(0);
        reloj.setAlCompletarCiclo(n -> ciclo.release());

        Thread.sleep(80);
        igual(0, c.tics, "sin iniciar, el reloj no avanza solo");
        reloj.paso();
        reloj.paso();
        reloj.paso();
        for (int i = 0; i < 3; i++) {
            ciclo.tryAcquire(ESPERA_MAXIMA_MS, TimeUnit.MILLISECONDS);
        }
        Thread.sleep(80);
        igual(3, c.tics, "tres pasos avanzan exactamente tres ciclos");

        reloj.iniciar();
        verificar(esperarCiclos(ciclo, 5), "en marcha el reloj avanza solo");
        reloj.pausar();
        Thread.sleep(80);
        ciclo.drainPermits();
        int congelado = c.tics;
        Thread.sleep(120);
        igual(congelado, c.tics, "en pausa el reloj no avanza");
        reloj.setVelocidadMs(1);
        igual(1L, reloj.getVelocidadMs(), "la duracion del ciclo se puede cambiar en ejecucion");
        reloj.detener();
    }

    private static boolean esperarCiclos(Semaphore ciclo, int cantidad) throws InterruptedException {
        for (int i = 0; i < cantidad; i++) {
            if (!ciclo.tryAcquire(ESPERA_MAXIMA_MS, TimeUnit.MILLISECONDS)) {
                return false;
            }
        }
        return true;
    }

    private static boolean todosTerminados(Proceso... procesos) {
        for (Proceso p : procesos) {
            if (p.getEstado() != EstadoProceso.TERMINADO) {
                return false;
            }
        }
        return true;
    }

    /** Repite una simulacion con 2 computadores en hilos distintos: si hubiera una carrera, fallaria alguna vez. */
    private static void simulacionesConHilos() throws InterruptedException {
        seccion("Simulaciones con un hilo por computador (30 repeticiones)");
        int terminadas = 0;
        int invariantesOk = 0;
        int sincronizadas = 0;
        final int repeticiones = 30;
        for (int r = 0; r < repeticiones; r++) {
            Cluster cluster = PruebasIntegracion.cluster(2, 200, 2);
            Computador pc1 = cluster.obtenerPorIndice(0);
            Computador pc2 = cluster.obtenerPorIndice(1);
            BufferSO b = cluster.crearBuffer("B1", 1, pc1.getId());
            Proceso p1 = PruebasIntegracion.comunicante("p1", b, true, 2, 3, 5000);
            Proceso p2 = PruebasIntegracion.comunicante("p2", b, true, 1, 3, 5000);
            Proceso c1 = PruebasIntegracion.comunicante("c1", b, false, 1, 3, 5000);
            Proceso c2 = PruebasIntegracion.comunicante("c2", b, false, 3, 3, 5000);
            pc1.recibirProceso(p1);
            pc1.recibirProceso(c1);
            pc2.recibirProceso(p2);
            pc2.recibirProceso(c2);

            RelojGlobal reloj = cluster.crearReloj(0);
            Semaphore fin = new Semaphore(0);
            boolean[] desfase = new boolean[1];
            reloj.setAlCompletarCiclo(ciclo -> {
                Nodo<Computador> n = cluster.getComputadores().getCabeza();
                while (n != null) {
                    if (n.getDato().getCiclosTotales() != ciclo) {
                        desfase[0] = true;
                    }
                    n = n.getSiguiente();
                }
                if (todosTerminados(p1, p2, c1, c2)) {
                    fin.release();
                }
            });
            reloj.iniciar();
            boolean ok = fin.tryAcquire(ESPERA_MAXIMA_MS, TimeUnit.MILLISECONDS);
            reloj.detener();
            if (ok) {
                terminadas++;
                if (b.getProducidos() == 6 && b.getConsumidos() == 6 && b.getElementos() == 0
                        && b.getVacios().getValor() == 1 && b.getLlenos().getValor() == 0
                        && b.getMutex().getValor() == 1) {
                    invariantesOk++;
                }
                if (!desfase[0]) {
                    sincronizadas++;
                }
            }
        }
        igual(repeticiones, terminadas, "las 30 simulaciones terminan (sin interbloqueos ni perdida de despertares)");
        igual(repeticiones, invariantesOk, "en las 30 se produjeron y consumieron 6 elementos y los semaforos quedaron intactos");
        igual(repeticiones, sincronizadas, "en todos los ciclos cada computador avanzo exactamente un ciclo con el reloj");
    }

    private static void registrosDeLaCpu() {
        seccion("Registros de la CPU");
        Cluster cluster = PruebasIntegracion.cluster(2, 100, 0);
        Computador pc = cluster.obtenerPorIndice(0);
        Proceso p = PruebasIntegracion.cpu("p", 10, 1000);
        pc.recibirProceso(p);
        for (int ciclo = 1; ciclo <= 3; ciclo++) {
            cluster.onTickGlobal(ciclo);
        }
        igual(3, pc.getCpu().getPc(), "el PC avanza una unidad por instruccion");
        igual(3, pc.getCpu().getMar(), "el MAR avanza junto con el PC");
        verificar(pc.getCpu().estaOcupada(), "la CPU esta ocupada con el proceso");
        igual(90, pc.getRam().getMemoriaDisponible(), "la RAM de B lleva la cuenta de la memoria");
    }
}

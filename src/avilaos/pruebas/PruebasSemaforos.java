package avilaos.pruebas;

import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.lanza;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.sincronizacion.SemaforoSO;
import avilaos.sincronizacion.TipoSemaforo;

public final class PruebasSemaforos {

    private PruebasSemaforos() {
    }

    private static Proceso proceso(String nombre) {
        return new Proceso(nombre, TipoProceso.CPU_BOUND, 1, 10, 100, 10);
    }

    public static void ejecutar() throws InterruptedException {
        seccion("Semaforos del SO");

        SemaforoSO conteo = new SemaforoSO("c", TipoSemaforo.CONTEO, 2);
        Proceso a = proceso("A");
        Proceso b = proceso("B");
        Proceso c = proceso("C");
        verificar(conteo.semWait(a), "semWait con valor 2 deja pasar al primero");
        verificar(conteo.semWait(b), "semWait con valor 1 deja pasar al segundo");
        verificar(!conteo.semWait(c), "semWait con valor 0 bloquea al tercero");
        igual(-1, conteo.getValor(), "el valor negativo cuenta los procesos en espera");
        igual(c, conteo.semSignal(), "semSignal despierta al proceso bloqueado");
        igual(null, c.getSemaforoBloqueo(), "el proceso despertado ya no esta ligado al semaforo");
        igual(null, conteo.semSignal(), "semSignal sin esperando no despierta a nadie");
        igual(1, conteo.getValor(), "el valor sube cuando no hay esperando");

        SemaforoSO fifo = new SemaforoSO("fifo", TipoSemaforo.CONTEO, 0);
        fifo.semWait(a);
        fifo.semWait(b);
        fifo.semWait(c);
        igual("P" + a.getId() + "-A, P" + b.getId() + "-B, P" + c.getId() + "-C", fifo.describirEspera(),
                "la cola del semaforo conserva el orden de llegada");
        igual(a, fifo.semSignal(), "se despierta primero al que llego primero (FIFO)");
        verificar(fifo.cancelarEspera(c), "cancelarEspera saca a un proceso de la cola");
        igual(1, fifo.getCantidadEnEspera(), "queda un proceso esperando tras cancelar");
        verificar(!fifo.cancelarEspera(c), "cancelar a quien no espera no hace nada");

        SemaforoSO mutex = new SemaforoSO("mutex", TipoSemaforo.BINARIO, 1);
        mutex.semSignal();
        igual(1, mutex.getValor(), "un semaforo binario no pasa de 1");
        verificar(mutex.semWait(a), "el mutex libre deja entrar");
        verificar(!mutex.semWait(b), "el mutex ocupado bloquea al segundo");
        igual(b, mutex.semSignal(), "al liberar el mutex despierta al que esperaba");

        lanza(IllegalArgumentException.class,
                () -> new SemaforoSO("x", TipoSemaforo.BINARIO, 2), "binario con valor inicial 2 es invalido");
        lanza(IllegalArgumentException.class,
                () -> new SemaforoSO(" ", TipoSemaforo.CONTEO, 0), "semaforo sin nombre es invalido");

        pruebaConcurrencia();
    }

    /** Varios hilos operan el mismo semaforo: no debe perderse ninguna actualizacion. */
    private static void pruebaConcurrencia() throws InterruptedException {
        final int hilos = 4;
        final int operaciones = 500;
        final SemaforoSO sem = new SemaforoSO("carrera", TipoSemaforo.CONTEO, hilos * operaciones);
        Thread[] esperan = new Thread[hilos];
        for (int i = 0; i < hilos; i++) {
            final Proceso p = proceso("w" + i);
            esperan[i] = new Thread(() -> {
                for (int k = 0; k < operaciones; k++) {
                    sem.semWait(p);
                }
            });
            esperan[i].start();
        }
        for (Thread t : esperan) {
            t.join();
        }
        igual(0, sem.getValor(), "4 hilos haciendo semWait a la vez no pierden decrementos");

        Thread[] avisan = new Thread[hilos];
        for (int i = 0; i < hilos; i++) {
            avisan[i] = new Thread(() -> {
                for (int k = 0; k < operaciones; k++) {
                    sem.semSignal();
                }
            });
            avisan[i].start();
        }
        for (Thread t : avisan) {
            t.join();
        }
        igual(hilos * operaciones, sem.getValor(), "4 hilos haciendo semSignal a la vez no pierden incrementos");
    }
}

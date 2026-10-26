package avilaos.gui;

import avilaos.estructuras.Nodo;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.Proceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;

/** Observa el cluster despues de cada ciclo y escribe en el log las decisiones importantes. */
public class RegistradorEventos {

    private final Cluster cluster;
    private final LogEventos log;
    private final Proceso[] enCpuAnterior;

    public RegistradorEventos(Cluster cluster, LogEventos log) {
        this.cluster = cluster;
        this.log = log;
        this.enCpuAnterior = new Proceso[cluster.getCantidadComputadores()];
    }

    public void observarCiclo() {
        int i = 0;
        Nodo<Computador> actual = cluster.getComputadores().getCabeza();
        while (actual != null) {
            observar(actual.getDato(), i);
            actual = actual.getSiguiente();
            i++;
        }
    }

    private void observar(Computador pc, int indice) {
        Proceso anterior = enCpuAnterior[indice];
        Proceso actual = pc.getProcesoEnCPU();

        // Terminaciones y bloqueos los registra el propio computador; aqui solo los cambios de CPU.
        if (anterior != null && anterior != actual && anterior.getEstado() == EstadoProceso.LISTO) {
            log.registrar(pc.getNombre() + " desaloja a " + anterior.getEtiqueta());
        }
        if (actual != null && actual != anterior) {
            log.registrar(pc.getNombre() + " selecciona el proceso " + actual.getEtiqueta());
        }
        enCpuAnterior[indice] = actual;
    }
}

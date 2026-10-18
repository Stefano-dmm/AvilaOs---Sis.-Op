package avilaos;

import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Computador;
import avilaos.nucleo.politicas.FCFS;

public class MainPrueba {

    public static void main(String[] args) {
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();

        System.out.println("=== AvilaOS - FCFS ===\n");

        Computador pc = new Computador("Nodo-A", 512, 4);
        pc.setPolitica(new FCFS(), PoliticaPlanificacion.FCFS);

        Proceso p1 = new Proceso("A", TipoProceso.CPU_BOUND, 1, 64, 30, 3);
        Proceso p2 = new Proceso("B", TipoProceso.CPU_BOUND, 1, 64, 30, 2);
        Proceso p3 = new Proceso("C", TipoProceso.CPU_BOUND, 1, 64, 30, 2);

        pc.recibirProceso(p1);
        pc.recibirProceso(p2);
        pc.recibirProceso(p3);

        System.out.println("Listos iniciales: " + pc.getColaListos());
        System.out.println();

        for (int ciclo = 1; ciclo <= 10; ciclo++) {
            pc.onTick(ciclo);
            String enCpu = pc.getProcesoEnCPU() == null ? "idle" : pc.getProcesoEnCPU().getEtiqueta();
            System.out.println("Ciclo " + ciclo
                    + " | CPU=" + enCpu
                    + " | listos=" + pc.getColaListos().getTamanio()
                    + " | term=" + pc.getColaTerminados().getTamanio());
        }

        System.out.println("\nTerminados (orden FCFS): " + pc.getColaTerminados());
        System.out.println("\nFCFS OK.");
    }
}

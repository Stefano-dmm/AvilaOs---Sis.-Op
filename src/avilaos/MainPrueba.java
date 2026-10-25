package avilaos;

import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.nucleo.politicas.FCFS;
import avilaos.nucleo.politicas.RoundRobin;

public class MainPrueba {

    public static void main(String[] args) {
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();

        System.out.println("=== AvilaOS - Asignacion manual a varios PC ===\n");

        Cluster cluster = new Cluster();

        Computador pc1 = new Computador("Nodo-A", 512, 2);
        Computador pc2 = new Computador("Nodo-B", 512, 2);
        pc1.setPolitica(new FCFS(), PoliticaPlanificacion.FCFS);
        pc2.setPolitica(new RoundRobin(), PoliticaPlanificacion.ROUND_ROBIN);

        cluster.agregarComputador(pc1);
        cluster.agregarComputador(pc2);

        Proceso a = new Proceso("Alpha", TipoProceso.CPU_BOUND, 1, 64, 30, 3);
        Proceso b = new Proceso("Beta", TipoProceso.CPU_BOUND, 1, 64, 30, 3);
        Proceso c = new Proceso("Gamma", TipoProceso.CPU_BOUND, 1, 64, 30, 4);

        cluster.asignarProcesoManual(a, pc1.getId());
        cluster.asignarProcesoManual(b, pc1.getId());
        cluster.asignarProcesoManual(c, pc2.getId());

        System.out.println("PC1 (FCFS): " + pc1);
        System.out.println("  Listos: " + pc1.getColaListos());
        System.out.println("PC2 (RR):  " + pc2);
        System.out.println("  Listos: " + pc2.getColaListos());
        System.out.println();

        for (int ciclo = 1; ciclo <= 8; ciclo++) {
            cluster.onTickGlobal(ciclo);
            System.out.println("Ciclo " + ciclo
                    + " | PC1=" + cpu(pc1)
                    + " | PC2=" + cpu(pc2));
        }

        System.out.println("\nPC1 terminados: " + pc1.getColaTerminados());
        System.out.println("PC2 terminados: " + pc2.getColaTerminados());
        System.out.println("Alpha en PC: " + a.getComputadorId()
                + " | Beta en PC: " + b.getComputadorId()
                + " | Gamma en PC: " + c.getComputadorId());
        System.out.println("\nAsignacion manual OK.");
    }

    private static String cpu(Computador pc) {
        return pc.getProcesoEnCPU() == null ? "idle" : pc.getProcesoEnCPU().getEtiqueta();
    }
}

package avilaos;

import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Computador;
import avilaos.nucleo.politicas.FCFS;
import avilaos.nucleo.politicas.PrioridadesApropiativas;

public class MainPrueba {

    public static void main(String[] args) {
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();

        System.out.println("=== AvilaOS - Cambio de politica en caliente ===\n");

        Computador pc = new Computador("Nodo-A", 512, 4);
        pc.setPolitica(new FCFS(), PoliticaPlanificacion.FCFS);

        Proceso bajo = new Proceso("Bajo", TipoProceso.CPU_BOUND, 1, 64, 40, 5);
        Proceso alto = new Proceso("Alto", TipoProceso.CPU_BOUND, 5, 64, 40, 3);
        Proceso medio = new Proceso("Medio", TipoProceso.CPU_BOUND, 3, 64, 40, 3);

        pc.recibirProceso(bajo);
        pc.recibirProceso(alto);
        pc.recibirProceso(medio);

        System.out.println("Politica inicial: " + pc.getNombrePolitica());
        System.out.println("Listos: " + pc.getColaListos());
        System.out.println();

        for (int ciclo = 1; ciclo <= 3; ciclo++) {
            pc.onTick(ciclo);
            System.out.println("Ciclo " + ciclo
                    + " | pol=" + pc.getNombrePolitica()
                    + " | CPU=" + cpu(pc)
                    + " | listos=" + pc.getColaListos().getTamanio());
        }

        System.out.println("\n>>> Cambio en caliente: FCFS -> PRIORIDADES");
        pc.setPolitica(new PrioridadesApropiativas(), PoliticaPlanificacion.PRIORIDADES);
        System.out.println("Politica actual: " + pc.getNombrePolitica());
        System.out.println("Colas conservadas | listos=" + pc.getColaListos()
                + " | enCPU=" + cpu(pc));
        System.out.println();

        for (int ciclo = 4; ciclo <= 12; ciclo++) {
            pc.onTick(ciclo);
            System.out.println("Ciclo " + ciclo
                    + " | pol=" + pc.getNombrePolitica()
                    + " | CPU=" + cpu(pc)
                    + " | listos=" + pc.getColaListos().getTamanio()
                    + " | term=" + pc.getColaTerminados().getTamanio());
        }

        System.out.println("\nTerminados: " + pc.getColaTerminados());
        System.out.println("\nCambio de politica en caliente OK.");
    }

    private static String cpu(Computador pc) {
        return pc.getProcesoEnCPU() == null ? "idle" : pc.getProcesoEnCPU().getEtiqueta();
    }
}

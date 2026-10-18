package avilaos;

import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Computador;

public class MainPrueba {

    public static void main(String[] args) {
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();

        System.out.println("=== AvilaOS - Admision (largo plazo) ===\n");

        Computador pc = new Computador("Nodo-A", 100, 4);

        Proceso p1 = new Proceso("Editor", TipoProceso.CPU_BOUND, 2, 60, 20, 10);
        Proceso p2 = new Proceso("Logger", TipoProceso.IO_BOUND, 1, 50, 15, 8);
        Proceso p3 = new Proceso("Sync", TipoProceso.CPU_BOUND, 3, 40, 25, 6);

        pc.recibirProceso(p1);
        pc.recibirProceso(p2);
        pc.recibirProceso(p3);

        System.out.println("Tras recibir 3 procesos (RAM=100):");
        System.out.println(pc);
        System.out.println("Nuevos: " + pc.getColaNuevos());
        System.out.println("Listos: " + pc.getColaListos());

        System.out.println("\nTermina P1 (libera 60):");
        pc.terminarProceso(p1);
        System.out.println(pc);
        System.out.println("Nuevos: " + pc.getColaNuevos());
        System.out.println("Listos: " + pc.getColaListos());
        System.out.println("Terminados: " + pc.getColaTerminados());

        System.out.println("\nAdmision OK.");
    }
}

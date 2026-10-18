package avilaos;

import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Computador;
import avilaos.nucleo.politicas.EDF;
import avilaos.nucleo.politicas.FCFS;
import avilaos.nucleo.politicas.PrioridadesApropiativas;
import avilaos.nucleo.politicas.RoundRobin;

public class MainPrueba {

    public static void main(String[] args) {
        System.out.println("========== PRUEBA EN FRIO AvilaOS ==========\n");

        boolean ok = true;
        ok &= probarEstructurasYPCB();
        ok &= probarAdmision();
        ok &= probarFCFS();
        ok &= probarEDF();
        ok &= probarRoundRobin();
        ok &= probarPrioridades();

        System.out.println("============================================");
        System.out.println(ok ? "RESULTADO: TODO OK" : "RESULTADO: HAY FALLOS");
    }

    private static boolean probarEstructurasYPCB() {
        System.out.println("--- 1) Estructuras y PCB ---");
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();

        Computador pc1 = new Computador("A", 1024, 4);
        Computador pc2 = new Computador("B", 2048, 4);
        Proceso p = new Proceso("Test", TipoProceso.CPU_BOUND, 1, 64, 10, 5);

        boolean ok = pc1.getId() == 1 && pc2.getId() == 2 && p.getId() == 1;
        System.out.println(ok ? "OK: 2 computadores + PCB con IDs unicos\n" : "FALLO estructuras/PCB\n");
        return ok;
    }

    private static boolean probarAdmision() {
        System.out.println("--- 2) Admision (largo plazo) ---");
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();

        Computador pc = new Computador("Nodo", 100, 4);
        Proceso p1 = new Proceso("P1", TipoProceso.CPU_BOUND, 1, 60, 20, 3);
        Proceso p2 = new Proceso("P2", TipoProceso.CPU_BOUND, 1, 50, 20, 3);
        Proceso p3 = new Proceso("P3", TipoProceso.CPU_BOUND, 1, 40, 20, 3);

        pc.recibirProceso(p1);
        pc.recibirProceso(p2);
        pc.recibirProceso(p3);

        boolean paso1 = pc.getColaListos().getTamanio() == 2
                && pc.getColaNuevos().getTamanio() == 1
                && pc.getRamUsada() == 100;

        pc.terminarProceso(p1);
        boolean paso2 = pc.getColaNuevos().getTamanio() == 0
                && pc.getColaListos().getTamanio() == 2
                && pc.getColaTerminados().getTamanio() == 1;

        boolean ok = paso1 && paso2;
        System.out.println(ok ? "OK: espera por RAM y readmite al liberar\n" : "FALLO admision\n");
        return ok;
    }

    private static boolean probarFCFS() {
        System.out.println("--- 3) FCFS ---");
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();

        Computador pc = new Computador("Nodo", 512, 4);
        pc.setPolitica(new FCFS(), PoliticaPlanificacion.FCFS);

        pc.recibirProceso(new Proceso("A", TipoProceso.CPU_BOUND, 1, 64, 30, 2));
        pc.recibirProceso(new Proceso("B", TipoProceso.CPU_BOUND, 1, 64, 30, 2));
        pc.recibirProceso(new Proceso("C", TipoProceso.CPU_BOUND, 1, 64, 30, 2));

        for (int i = 1; i <= 10; i++) {
            pc.onTick(i);
        }

        String orden = pc.getColaTerminados().toString();
        boolean ok = orden.contains("P1-A") && orden.contains("P2-B") && orden.contains("P3-C")
                && orden.indexOf("P1-A") < orden.indexOf("P2-B")
                && orden.indexOf("P2-B") < orden.indexOf("P3-C")
                && pc.getColaTerminados().getTamanio() == 3;

        System.out.println(ok ? "OK: terminan A -> B -> C\n" : "FALLO FCFS: " + orden + "\n");
        return ok;
    }

    private static boolean probarEDF() {
        System.out.println("--- 4) EDF ---");
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();

        Computador pc = new Computador("Nodo", 512, 4);
        pc.setPolitica(new EDF(), PoliticaPlanificacion.EDF);

        pc.recibirProceso(new Proceso("Largo", TipoProceso.CPU_BOUND, 1, 64, 20, 3));
        pc.recibirProceso(new Proceso("Urgente", TipoProceso.CPU_BOUND, 1, 64, 5, 2));
        pc.recibirProceso(new Proceso("Medio", TipoProceso.CPU_BOUND, 1, 64, 10, 2));

        for (int i = 1; i <= 10; i++) {
            pc.onTick(i);
        }

        String orden = pc.getColaTerminados().toString();
        boolean ok = orden.indexOf("Urgente") < orden.indexOf("Medio")
                && orden.indexOf("Medio") < orden.indexOf("Largo")
                && pc.getColaTerminados().getTamanio() == 3;

        System.out.println(ok ? "OK: Urgente -> Medio -> Largo\n" : "FALLO EDF: " + orden + "\n");
        return ok;
    }

    private static boolean probarRoundRobin() {
        System.out.println("--- 5) Round Robin ---");
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();

        Computador pc = new Computador("Nodo", 512, 2);
        pc.setPolitica(new RoundRobin(), PoliticaPlanificacion.ROUND_ROBIN);

        pc.recibirProceso(new Proceso("A", TipoProceso.CPU_BOUND, 1, 64, 30, 4));
        pc.recibirProceso(new Proceso("B", TipoProceso.CPU_BOUND, 1, 64, 30, 4));
        pc.recibirProceso(new Proceso("C", TipoProceso.CPU_BOUND, 1, 64, 30, 2));

        StringBuilder traza = new StringBuilder();
        for (int i = 1; i <= 12; i++) {
            pc.onTick(i);
            if (pc.getProcesoEnCPU() != null) {
                traza.append(pc.getProcesoEnCPU().getNombre());
            }
        }

        boolean ok = pc.getColaTerminados().getTamanio() == 3
                && traza.toString().contains("AB")
                && traza.indexOf("A") < traza.indexOf("B");

        System.out.println(ok ? "OK: rota por quantum y terminan los 3\n"
                : "FALLO RR: traza=" + traza + " term=" + pc.getColaTerminados() + "\n");
        return ok;
    }

    private static boolean probarPrioridades() {
        System.out.println("--- 6) Prioridades apropiativas ---");
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();

        Computador pc = new Computador("Nodo", 512, 4);
        pc.setPolitica(new PrioridadesApropiativas(), PoliticaPlanificacion.PRIORIDADES);

        Proceso bajo = new Proceso("Bajo", TipoProceso.CPU_BOUND, 1, 64, 40, 6);
        pc.recibirProceso(bajo);
        pc.onTick(1);

        pc.recibirProceso(new Proceso("Alto", TipoProceso.CPU_BOUND, 5, 64, 40, 2));
        pc.recibirProceso(new Proceso("Medio", TipoProceso.CPU_BOUND, 3, 64, 40, 2));

        for (int i = 2; i <= 12; i++) {
            pc.onTick(i);
        }

        String orden = pc.getColaTerminados().toString();
        boolean ok = orden.indexOf("Alto") < orden.indexOf("Medio")
                && orden.indexOf("Medio") < orden.indexOf("Bajo")
                && pc.getColaTerminados().getTamanio() == 3;

        System.out.println(ok ? "OK: Alto desaloja a Bajo; orden Alto -> Medio -> Bajo\n"
                : "FALLO Prioridades: " + orden + "\n");
        return ok;
    }
}

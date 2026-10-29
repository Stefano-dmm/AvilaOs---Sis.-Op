package avilaos.pruebas;

import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.modelo.ModoCPU;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Computador;
import avilaos.nucleo.programas.ProgramaIoBound;

/**
 * Cambio de contexto: cada vez que la CPU cambia de proceso el log dice que contexto se guarda en el PCB (PC)
 * y cual se restaura, y el PC que se restaura es el que se guardo.
 */
public final class PruebasContexto {

    private PruebasContexto() {
    }

    /** Captura los eventos del log del cluster de un computador. */
    private static StringBuilder capturar(Computador pc) {
        StringBuilder eventos = new StringBuilder();
        pc.getCluster().setOyenteEventos(m -> eventos.append(m).append('\n'));
        return eventos;
    }

    private static void avanzar(Computador pc, int desde, int hasta) {
        for (int ciclo = desde; ciclo <= hasta; ciclo++) {
            pc.getCluster().onTickGlobal(ciclo);
        }
    }

    public static void ejecutar() {
        roundRobin();
        prioridadesApropiativas();
        bloqueoDeES();
        deadlineEnListos();
    }

    private static void roundRobin() {
        seccion("Cambio de contexto con Round Robin");
        Computador pc = PruebasPoliticas.computador(PoliticaPlanificacion.ROUND_ROBIN, 2);
        StringBuilder log = capturar(pc);
        Proceso a = PruebasPoliticas.proceso("A", 4, 1, 100);
        Proceso b = PruebasPoliticas.proceso("B", 4, 1, 100);
        pc.recibirProceso(a);
        pc.recibirProceso(b);
        avanzar(pc, 1, 8);
        String eventos = log.toString();

        verificar(eventos.contains("PC selecciona el proceso P1-A y carga su contexto (PC restaurado=0, MAR=0)"),
                "al empezar carga el contexto de A con el PC en 0");
        verificar(eventos.contains("PC guarda el contexto de P1-A en su PCB (PC=2) y lo devuelve a Listos"),
                "al vencer el quantum guarda el contexto de A con su PC=2");
        verificar(eventos.contains("PC selecciona el proceso P2-B y carga su contexto (PC restaurado=0, MAR=0)"),
                "carga el contexto de B desde el principio");
        verificar(eventos.contains("PC guarda el contexto de P2-B en su PCB (PC=2) y lo devuelve a Listos"),
                "al vencer el quantum de B guarda su PC=2");
        verificar(eventos.contains("PC selecciona el proceso P1-A y carga su contexto (PC restaurado=2, MAR=2)"),
                "cuando A vuelve a la CPU se restaura el PC=2 que se habia guardado");
        verificar(eventos.contains("P1-A completa sus instrucciones y termina en PC (PC final=4)"),
                "A termina con el PC final igual a sus 4 instrucciones");
        verificar(eventos.indexOf("guarda el contexto de P1-A") < eventos.indexOf("selecciona el proceso P2-B"),
                "primero se guarda el contexto del que sale y despues se carga el del que entra");
    }

    private static void prioridadesApropiativas() {
        seccion("Cambio de contexto por prioridad apropiativa");
        Computador pc = PruebasPoliticas.computador(PoliticaPlanificacion.PRIORIDADES, 2);
        StringBuilder log = capturar(pc);
        Proceso bajo = PruebasPoliticas.proceso("bajo", 6, 1, 100);
        pc.recibirProceso(bajo);
        avanzar(pc, 1, 2);
        igual(2, pc.getCpu().getPc(), "tras dos instrucciones el PC de la CPU es 2");
        igual(2, pc.getCpu().getMar(), "y el MAR avanza junto con el PC");
        igual(ModoCPU.USUARIO, pc.getModoCPU(), "ejecutando un programa de usuario");
        Proceso alto = PruebasPoliticas.proceso("alto", 2, 5, 100);
        pc.recibirProceso(alto);
        avanzar(pc, 3, 8);
        String eventos = log.toString();

        verificar(eventos.contains("PC guarda el contexto de P1-bajo en su PCB (PC=2) y lo devuelve a Listos"),
                "al llegar un proceso de mayor prioridad se guarda el contexto del que ejecutaba (PC=2)");
        verificar(eventos.contains("PC selecciona el proceso P2-alto y carga su contexto (PC restaurado=0, MAR=0)"),
                "se carga el contexto del proceso de prioridad alta");
        verificar(eventos.contains("PC selecciona el proceso P1-bajo y carga su contexto (PC restaurado=2, MAR=2)"),
                "el proceso desalojado retoma exactamente donde iba: PC=2");
        verificar(eventos.contains("P1-bajo completa sus instrucciones y termina en PC (PC final=6)"),
                "y termina con sus 6 instrucciones completas, sin repetir ni perder ninguna");
        igual(6, bajo.getPc(), "el PC del proceso desalojado llego a 6");
    }

    private static void bloqueoDeES() {
        seccion("Contexto guardado al bloquearse");
        Computador pc = PruebasPoliticas.computador(PoliticaPlanificacion.FCFS, 2);
        StringBuilder log = capturar(pc);
        Proceso io = new Proceso("io", TipoProceso.IO_BOUND, 1, 10, 100, 6);
        io.setPrograma(new ProgramaIoBound(2, 3));
        Proceso otro = PruebasPoliticas.proceso("otro", 4, 1, 100);
        pc.recibirProceso(io);
        pc.recibirProceso(otro);
        avanzar(pc, 1, 3);
        String eventos = log.toString();
        verificar(eventos.contains("P1-io se bloquea: E/S de 3 ciclos (contexto guardado en su PCB: PC=2)"),
                "al pedir E/S guarda su contexto con PC=2");
        verificar(eventos.contains("PC selecciona el proceso P2-otro y carga su contexto (PC restaurado=0, MAR=0)"),
                "mientras espera la E/S la CPU carga el contexto de otro proceso");
        igual(2, io.getPc(), "el PC del proceso bloqueado se conserva en su PCB");
    }

    private static void deadlineEnListos() {
        seccion("Proceso que muere por deadline esperando");
        Computador pc = PruebasPoliticas.computador(PoliticaPlanificacion.FCFS, 2);
        StringBuilder log = capturar(pc);
        pc.recibirProceso(PruebasPoliticas.proceso("largo", 50, 1, 1000));
        pc.recibirProceso(PruebasPoliticas.proceso("corto", 5, 1, 3));
        avanzar(pc, 1, 3);
        verificar(log.toString().contains("P2-corto llega a su deadline en PC y es terminado (PC=0, le faltaban 5 instrucciones)"),
                "el log dice que el proceso que nunca ejecuto murio con PC=0 y 5 instrucciones pendientes");
    }
}

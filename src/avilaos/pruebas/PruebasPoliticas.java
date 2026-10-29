package avilaos.pruebas;

import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.metricas.CalculadorMetricas;
import avilaos.metricas.ResumenMetricas;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.nucleo.politicas.FabricaPoliticas;
import avilaos.nucleo.programas.ProgramaCpuBound;

/** Comportamiento de las cuatro politicas de planificacion con escenarios calculados a mano. */
public final class PruebasPoliticas {

    private PruebasPoliticas() {
    }

    static Computador computador(PoliticaPlanificacion politica, int quantum) {
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();
        Cluster cluster = new Cluster();
        Computador pc = new Computador("PC", 1000, quantum);
        pc.setPolitica(FabricaPoliticas.crear(politica), politica);
        cluster.agregarComputador(pc);
        return pc;
    }

    static Proceso proceso(String nombre, int instrucciones, int prioridad, int deadline) {
        Proceso p = new Proceso(nombre, TipoProceso.CPU_BOUND, prioridad, 10, deadline, instrucciones);
        p.setPrograma(new ProgramaCpuBound());
        return p;
    }

    private static void avanzar(Computador pc, int desde, int hasta) {
        for (int ciclo = desde; ciclo <= hasta; ciclo++) {
            pc.getCluster().onTickGlobal(ciclo);
        }
    }

    public static void ejecutar() {
        fcfs();
        edf();
        roundRobin();
        prioridades();
        cambioEnCaliente();
    }

    private static void fcfs() {
        seccion("Politica FCFS");
        Computador pc = computador(PoliticaPlanificacion.FCFS, 2);
        Proceso a = proceso("A", 3, 1, 100);
        Proceso b = proceso("B", 2, 1, 100);
        Proceso c = proceso("C", 1, 1, 100);
        pc.recibirProceso(a);
        pc.recibirProceso(b);
        pc.recibirProceso(c);
        avanzar(pc, 1, 6);
        igual(3, a.getCicloFin(), "A, el primero en llegar, termina primero (ciclo 3)");
        igual(5, b.getCicloFin(), "B termina despues (ciclo 5)");
        igual(6, c.getCicloFin(), "C, aunque es el mas corto, espera su turno (ciclo 6)");
        igual(3, b.getCicloPrimerDespacho(), "B solo se despacha cuando A termina");
    }

    private static void edf() {
        seccion("Politica EDF (primero el deadline mas cercano)");
        Computador pc = computador(PoliticaPlanificacion.EDF, 2);
        Proceso a = proceso("A", 3, 1, 30);
        Proceso b = proceso("B", 3, 1, 10);
        Proceso c = proceso("C", 3, 1, 20);
        pc.recibirProceso(a);
        pc.recibirProceso(b);
        pc.recibirProceso(c);
        avanzar(pc, 1, 9);
        igual(3, b.getCicloFin(), "B (deadline 10) va primero aunque llego segundo");
        igual(6, c.getCicloFin(), "C (deadline 20) va despues");
        igual(9, a.getCicloFin(), "A (deadline 30) va al final");

        // Con un deadline ajustado, FCFS deja morir a B y EDF no.
        Computador conEdf = computador(PoliticaPlanificacion.EDF, 2);
        Proceso largoE = proceso("largo", 4, 1, 50);
        Proceso urgenteE = proceso("urgente", 2, 1, 3);
        conEdf.recibirProceso(largoE);
        conEdf.recibirProceso(urgenteE);
        avanzar(conEdf, 1, 6);
        verificar(urgenteE.cumplioDeadline(), "EDF: el proceso urgente cumple su deadline");

        Computador conFcfs = computador(PoliticaPlanificacion.FCFS, 2);
        Proceso largoF = proceso("largo", 4, 1, 50);
        Proceso urgenteF = proceso("urgente", 2, 1, 3);
        conFcfs.recibirProceso(largoF);
        conFcfs.recibirProceso(urgenteF);
        avanzar(conFcfs, 1, 6);
        verificar(!urgenteF.cumplioDeadline(), "FCFS: el mismo proceso urgente vence su deadline esperando");
        igual(EstadoProceso.TERMINADO, urgenteF.getEstado(), "y es terminado por el sistema");
        casiMejor(CalculadorMetricas.deComputador(conEdf), CalculadorMetricas.deComputador(conFcfs));
    }

    private static void casiMejor(ResumenMetricas edf, ResumenMetricas fcfs) {
        verificar(edf.cumplimiento() > fcfs.cumplimiento(), "EDF cumple mas deadlines que FCFS en este escenario");
    }

    private static void roundRobin() {
        seccion("Politica Round Robin");
        Computador rr = computador(PoliticaPlanificacion.ROUND_ROBIN, 2);
        Proceso a = proceso("A", 4, 1, 100);
        Proceso b = proceso("B", 4, 1, 100);
        rr.recibirProceso(a);
        rr.recibirProceso(b);
        avanzar(rr, 1, 8);
        igual(2, b.getCicloPrimerDespacho(), "B recibe la CPU al vencer el quantum de A (ciclo 2)");
        igual(6, a.getCicloFin(), "A alterna con B y termina en el ciclo 6");
        igual(8, b.getCicloFin(), "B termina en el ciclo 8");

        Computador fcfs = computador(PoliticaPlanificacion.FCFS, 2);
        Proceso af = proceso("A", 4, 1, 100);
        Proceso bf = proceso("B", 4, 1, 100);
        fcfs.recibirProceso(af);
        fcfs.recibirProceso(bf);
        avanzar(fcfs, 1, 8);
        verificar(b.getCicloPrimerDespacho() < bf.getCicloPrimerDespacho(),
                "con RR el segundo proceso empieza antes que con FCFS (mejor tiempo de respuesta)");
        verificar(a.getCicloFin() > af.getCicloFin(), "a cambio, el primero termina despues que con FCFS");

        Computador unico = computador(PoliticaPlanificacion.ROUND_ROBIN, 3);
        Proceso solo = proceso("solo", 5, 1, 100);
        unico.recibirProceso(solo);
        avanzar(unico, 1, 5);
        igual(5, solo.getCicloFin(), "un proceso solo no pierde ciclos al vencer su quantum");
    }

    private static void prioridades() {
        seccion("Politica de prioridades apropiativas");
        Computador pc = computador(PoliticaPlanificacion.PRIORIDADES, 2);
        Proceso bajo = proceso("bajo", 6, 1, 100);
        pc.recibirProceso(bajo);
        avanzar(pc, 1, 2);
        igual(bajo, pc.getProcesoEnCPU(), "el proceso de baja prioridad ejecuta mientras esta solo");
        Proceso alto = proceso("alto", 2, 5, 100);
        pc.recibirProceso(alto);
        avanzar(pc, 3, 3);
        igual(alto, pc.getProcesoEnCPU(), "al llegar uno de mayor prioridad le quita la CPU de inmediato");
        igual(EstadoProceso.LISTO, bajo.getEstado(), "el desalojado vuelve a Listos");
        avanzar(pc, 4, 8);
        igual(4, alto.getCicloFin(), "el de prioridad alta termina en el ciclo 4");
        igual(8, bajo.getCicloFin(), "el de baja prioridad retoma y termina en el 8");
        igual(2, bajo.getCiclosEjecutados() - 4, "sin perder las 2 instrucciones que ya habia ejecutado");

        Computador fcfs = computador(PoliticaPlanificacion.FCFS, 2);
        Proceso bajoF = proceso("bajo", 6, 1, 100);
        fcfs.recibirProceso(bajoF);
        avanzar(fcfs, 1, 2);
        Proceso altoF = proceso("alto", 2, 5, 100);
        fcfs.recibirProceso(altoF);
        avanzar(fcfs, 3, 8);
        verificar(altoF.getCicloFin() > alto.getCicloFin(), "con FCFS el mismo proceso de prioridad alta termina mas tarde");

        Computador empate = computador(PoliticaPlanificacion.PRIORIDADES, 2);
        Proceso p1 = proceso("p1", 2, 3, 100);
        Proceso p2 = proceso("p2", 2, 3, 100);
        empate.recibirProceso(p1);
        empate.recibirProceso(p2);
        avanzar(empate, 1, 4);
        verificar(p1.getCicloFin() < p2.getCicloFin(), "a igual prioridad se respeta el orden de llegada y no hay desalojo");
    }

    private static void cambioEnCaliente() {
        seccion("Cambio de politica y de quantum en tiempo de ejecucion");
        Computador pc = computador(PoliticaPlanificacion.FCFS, 2);
        Proceso corriendo = proceso("corriendo", 4, 1, 200);
        Proceso lejano = proceso("lejano", 2, 1, 150);
        Proceso cercano = proceso("cercano", 2, 1, 20);
        pc.recibirProceso(corriendo);
        pc.recibirProceso(lejano);
        pc.recibirProceso(cercano);
        avanzar(pc, 1, 2);
        pc.setPolitica(FabricaPoliticas.crear(PoliticaPlanificacion.EDF), PoliticaPlanificacion.EDF);
        igual(PoliticaPlanificacion.EDF, pc.getNombrePolitica(), "la politica del computador cambio a EDF");
        avanzar(pc, 3, 8);
        verificar(cercano.getCicloFin() < lejano.getCicloFin(),
                "tras el cambio, el siguiente en ejecutar es el de deadline mas cercano aunque llego despues");

        Computador rr = computador(PoliticaPlanificacion.ROUND_ROBIN, 4);
        Proceso a = proceso("A", 6, 1, 200);
        Proceso b = proceso("B", 6, 1, 200);
        rr.recibirProceso(a);
        rr.recibirProceso(b);
        avanzar(rr, 1, 2);
        rr.setQuantum(1);
        igual(1, rr.getQuantum(), "el quantum se puede cambiar en ejecucion");
        avanzar(rr, 3, 3);
        igual(b, rr.getProcesoEnCPU(), "con quantum 1 la CPU alterna en cuanto se aplica el cambio");
    }
}

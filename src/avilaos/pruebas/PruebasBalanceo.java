package avilaos.pruebas;

import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.lanza;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.nucleo.politicas.FabricaPoliticas;

/** Pruebas de la asignacion automatica de procesos (balanceo de carga por menor carga). */
public final class PruebasBalanceo {

    private PruebasBalanceo() {
    }

    private static Cluster cluster(int... rams) {
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();
        Cluster cluster = new Cluster();
        for (int i = 0; i < rams.length; i++) {
            Computador pc = new Computador("PC" + (i + 1), rams[i], 2);
            pc.setPolitica(FabricaPoliticas.crear(PoliticaPlanificacion.FCFS), PoliticaPlanificacion.FCFS);
            cluster.agregarComputador(pc);
        }
        return cluster;
    }

    private static Proceso proceso(String nombre, int memoria) {
        return new Proceso(nombre, TipoProceso.CPU_BOUND, 1, memoria, 1000, 50);
    }

    public static void ejecutar() {
        seccion("Balanceo de carga: asignacion automatica");

        Cluster cluster = cluster(300, 300, 300);
        for (int i = 0; i < 6; i++) {
            cluster.asignarProcesoAutomatico(proceso("p" + i, 10));
        }
        igual(2, cluster.obtenerPorIndice(0).getCargaActual(), "6 procesos iguales se reparten: 2 en el PC1");
        igual(2, cluster.obtenerPorIndice(1).getCargaActual(), "2 en el PC2");
        igual(2, cluster.obtenerPorIndice(2).getCargaActual(), "y 2 en el PC3");

        Cluster desbalanceado = cluster(300, 300);
        desbalanceado.obtenerPorIndice(0).recibirProceso(proceso("a", 10));
        desbalanceado.obtenerPorIndice(0).recibirProceso(proceso("b", 10));
        Computador elegido = desbalanceado.asignarProcesoAutomatico(proceso("nuevo", 10));
        igual("PC2", elegido.getNombre(), "el proceso nuevo va al computador con menos procesos");

        Cluster desigual = cluster(100, 300);
        igual("PC2", desigual.asignarProcesoAutomatico(proceso("x", 10)).getNombre(),
                "a igual carga gana el computador con mas RAM libre");
        Cluster empate = cluster(200, 200);
        igual("PC1", empate.asignarProcesoAutomatico(proceso("x", 10)).getNombre(),
                "a igual carga y RAM gana el de menor id (eleccion predecible)");

        Cluster pequeno = cluster(100, 300);
        pequeno.obtenerPorIndice(1).recibirProceso(proceso("ocupa", 10));
        pequeno.obtenerPorIndice(1).recibirProceso(proceso("ocupa2", 10));
        igual("PC2", pequeno.asignarProcesoAutomatico(proceso("grande", 150)).getNombre(),
                "se descarta el computador cuya RAM total no alcanza, aunque este menos cargado");
        lanza(IllegalArgumentException.class, () -> pequeno.asignarProcesoAutomatico(proceso("enorme", 1000)),
                "si ningun computador tiene RAM suficiente se rechaza el proceso");

        Cluster terminados = cluster(300, 300);
        Proceso corto = new Proceso("corto", TipoProceso.CPU_BOUND, 1, 10, 1000, 1);
        terminados.obtenerPorIndice(0).recibirProceso(corto);
        terminados.onTickGlobal(1);
        igual(EstadoDe.terminado(corto), true, "el proceso corto termino");
        igual(0, terminados.obtenerPorIndice(0).getCargaActual(), "un proceso terminado ya no cuenta como carga");
        igual("PC1", terminados.asignarProcesoAutomatico(proceso("y", 10)).getNombre(),
                "con la carga liberada el PC1 vuelve a estar en la competencia");

        Cluster bloqueo = cluster(300, 300);
        Proceso p = proceso("fijo", 10);
        Computador destino = bloqueo.asignarProcesoAutomatico(p);
        igual(destino.getId(), p.getComputadorId(), "el proceso queda asignado a su computador");
        lanza(IllegalStateException.class, () -> bloqueo.asignarProcesoAutomatico(p),
                "no hay migracion: un proceso ya asignado no se vuelve a asignar");
        lanza(IllegalStateException.class, () -> bloqueo.asignarProcesoManual(p, 1),
                "tampoco se puede reasignar a mano");

        Cluster eventos = cluster(300, 300);
        StringBuilder log = new StringBuilder();
        eventos.setOyenteEventos(m -> log.append(m).append('\n'));
        eventos.asignarProcesoAutomatico(proceso("log", 10));
        verificar(log.toString().contains("AvilaOS asigna"), "la decision queda registrada en el log de eventos");

        Cluster otro = cluster(300, 300, 300);
        otro.setCriterio((computadores, proc) -> computadores.obtener(2));
        igual("PC3", otro.asignarProcesoAutomatico(proceso("z", 10)).getNombre(),
                "el criterio es intercambiable sin modificar el cluster");
        lanza(IllegalArgumentException.class, () -> otro.setCriterio(null), "un criterio nulo es invalido");
    }

    /** Pequena ayuda para escribir la comprobacion de estado sin importar el enum en cada prueba. */
    private static final class EstadoDe {

        static boolean terminado(Proceso p) {
            return p.getEstado() == avilaos.modelo.EstadoProceso.TERMINADO;
        }
    }
}

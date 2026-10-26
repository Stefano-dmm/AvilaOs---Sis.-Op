package avilaos.config;

import avilaos.estructuras.Nodo;
import avilaos.modelo.Proceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.nucleo.politicas.FabricaPoliticas;

/** Convierte una Configuracion en un Cluster listo para simular. */
public final class ConstructorCluster {

    private ConstructorCluster() {
    }

    public static Cluster construir(Configuracion config) {
        config.validarCompleta();
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();

        Cluster cluster = new Cluster();
        cluster.setLatenciaRed(config.getLatenciaRed());
        Nodo<ConfigComputador> nodoPc = config.getComputadores().getCabeza();
        while (nodoPc != null) {
            ConfigComputador c = nodoPc.getDato();
            Computador pc = new Computador(c.nombre(), c.ram(), c.quantum());
            pc.setPolitica(FabricaPoliticas.crear(c.politica()), c.politica());
            cluster.agregarComputador(pc);
            nodoPc = nodoPc.getSiguiente();
        }

        Nodo<ConfigProceso> nodoPr = config.getProcesos().getCabeza();
        while (nodoPr != null) {
            ConfigProceso p = nodoPr.getDato();
            Proceso proceso = new Proceso(p.nombre(), p.tipo(), p.prioridad(),
                    p.memoria(), p.deadline(), p.instrucciones());
            cluster.asignarProcesoManual(proceso, cluster.obtenerPorIndice(p.indiceComputador()).getId());
            nodoPr = nodoPr.getSiguiente();
        }
        return cluster;
    }
}

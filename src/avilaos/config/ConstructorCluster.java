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

        Nodo<ConfigBuffer> nodoBuf = config.getBuffers().getCabeza();
        while (nodoBuf != null) {
            ConfigBuffer b = nodoBuf.getDato();
            cluster.crearBuffer(b.nombre(), b.capacidad(), cluster.obtenerPorIndice(b.indiceComputador()).getId());
            nodoBuf = nodoBuf.getSiguiente();
        }

        Nodo<ConfigProceso> nodoPr = config.getProcesos().getCabeza();
        while (nodoPr != null) {
            ConfigProceso p = nodoPr.getDato();
            Proceso proceso = FabricaProcesos.crear(p, cluster);
            if (p.indiceComputador() >= 0) {
                cluster.asignarProcesoManual(proceso, cluster.obtenerPorIndice(p.indiceComputador()).getId());
            } else {
                cluster.asignarProcesoAutomatico(proceso);
            }
            nodoPr = nodoPr.getSiguiente();
        }
        return cluster;
    }
}

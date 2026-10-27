package avilaos.config;

import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.programas.ProgramaCpuBound;
import avilaos.nucleo.programas.ProgramaIoBound;
import avilaos.nucleo.programas.ProgramaProductorConsumidor;
import avilaos.sincronizacion.BufferSO;

/** Crea el proceso (con el programa que le corresponde a su tipo) a partir de su configuracion. */
public final class FabricaProcesos {

    /** Clase de utilidades: no se instancia. */
    private FabricaProcesos() {
    }

    /**
     * Crea el proceso y le pone el programa que corresponde a su tipo (CPU, E/S o productor-consumidor con su buffer). Para
     * productores y consumidores calcula las instrucciones totales a partir de los elementos y el intervalo.
     */
    public static Proceso crear(ConfigProceso c, Cluster cluster) {
        Proceso proceso;
        switch (c.tipo()) {
            case IO_BOUND -> {
                proceso = new Proceso(c.nombre(), c.tipo(), c.prioridad(), c.memoria(), c.deadline(), c.instrucciones());
                proceso.setPrograma(new ProgramaIoBound(c.intervalo(), c.ciclosES()));
            }
            case PRODUCTOR, CONSUMIDOR -> {
                BufferSO buffer = cluster.obtenerBuffer(c.indiceBuffer());
                if (buffer == null) {
                    throw new IllegalArgumentException("El proceso " + c.nombre() + " apunta a un buffer que no existe.");
                }
                int instrucciones = ProgramaProductorConsumidor.instruccionesTotales(c.intervalo(), c.elementos());
                proceso = new Proceso(c.nombre(), c.tipo(), c.prioridad(), c.memoria(), c.deadline(), instrucciones);
                proceso.setPrograma(new ProgramaProductorConsumidor(c.tipo() == TipoProceso.PRODUCTOR, buffer, c.intervalo()));
            }
            default -> {
                proceso = new Proceso(c.nombre(), c.tipo(), c.prioridad(), c.memoria(), c.deadline(), c.instrucciones());
                proceso.setPrograma(new ProgramaCpuBound());
            }
        }
        return proceso;
    }
}

package avilaos.gui;

import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.nucleo.politicas.FabricaPoliticas;
import javax.swing.SwingUtilities;

/** Punto de entrada de la interfaz grafica. Carga un cluster de ejemplo hasta tener la vista de configuracion. */
public class AvilaOSGui {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Cluster cluster = crearClusterDeEjemplo();
            new VentanaPrincipal(cluster).setVisible(true);
        });
    }

    private static Cluster crearClusterDeEjemplo() {
        Cluster cluster = new Cluster();
        Computador a = new Computador("Nodo-A", 512, 3);
        Computador b = new Computador("Nodo-B", 512, 3);
        a.setPolitica(FabricaPoliticas.crear(PoliticaPlanificacion.FCFS), PoliticaPlanificacion.FCFS);
        b.setPolitica(FabricaPoliticas.crear(PoliticaPlanificacion.ROUND_ROBIN), PoliticaPlanificacion.ROUND_ROBIN);
        cluster.agregarComputador(a);
        cluster.agregarComputador(b);

        cluster.asignarProcesoManual(new Proceso("Alpha", TipoProceso.CPU_BOUND, 1, 128, 40, 8), a.getId());
        cluster.asignarProcesoManual(new Proceso("Beta", TipoProceso.CPU_BOUND, 2, 128, 40, 6), a.getId());
        cluster.asignarProcesoManual(new Proceso("Gamma", TipoProceso.CPU_BOUND, 1, 256, 40, 7), b.getId());
        cluster.asignarProcesoManual(new Proceso("Delta", TipoProceso.CPU_BOUND, 3, 512, 40, 5), b.getId());
        return cluster;
    }
}

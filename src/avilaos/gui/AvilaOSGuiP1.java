package avilaos.gui;

import avilaos.config.Configuracion;
import avilaos.config.ConstructorCluster;
import avilaos.nucleo.Cluster;
import javax.swing.SwingUtilities;

/** Punto de entrada del Proyecto 1 (simulador distribuido con configuración manual); se conserva para compararlo con el P2. */
public class AvilaOSGuiP1 {

    /** Abre la ventana de configuración del Proyecto 1 en el hilo de Swing. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() ->
                new VentanaConfiguracion(Configuracion.porDefecto(), AvilaOSGuiP1::iniciarSimulacion)
                        .setVisible(true));
    }

    /** Se llama al pulsar Iniciar en la configuración: construye el cluster y abre la ventana principal del P1. */
    private static void iniciarSimulacion(Configuracion config) {
        Cluster cluster = ConstructorCluster.construir(config);
        new VentanaPrincipal(cluster, config).setVisible(true);
    }
}

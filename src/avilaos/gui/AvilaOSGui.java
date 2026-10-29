package avilaos.gui;

import avilaos.config.Configuracion;
import avilaos.config.ConstructorCluster;
import avilaos.nucleo.Cluster;
import javax.swing.SwingUtilities;

/** Punto de entrada de la interfaz grafica: primero la configuracion y luego la simulacion. */
public class AvilaOSGui {

    /** Punto de entrada de la aplicacion: abre la ventana de configuracion en el hilo de Swing. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() ->
                new VentanaConfiguracion(Configuracion.porDefecto(), AvilaOSGui::iniciarSimulacion)
                        .setVisible(true));
    }

    /** Se llama al pulsar Iniciar en la configuracion: construye el cluster y abre la ventana principal. */
    private static void iniciarSimulacion(Configuracion config) {
        Cluster cluster = ConstructorCluster.construir(config);
        new VentanaPrincipal(cluster, config).setVisible(true);
    }
}

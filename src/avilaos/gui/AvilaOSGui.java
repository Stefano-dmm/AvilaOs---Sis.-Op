package avilaos.gui;

import javax.swing.SwingUtilities;

/** Punto de entrada de ÁvilaOS (Proyecto 2): primero se cargan los dos JSON y luego arranca la simulación. */
public class AvilaOSGui {

    /** Abre la ventana de carga de los JSON en el hilo de Swing. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VentanaCargaP2().setVisible(true));
    }
}

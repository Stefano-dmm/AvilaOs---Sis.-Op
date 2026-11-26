package avilaos.gui;

import avilaos.disco.Disco;
import avilaos.disco.PoliticaDisco;
import avilaos.nucleo.Computador;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Métricas del disco y de la suspensión: desplazamiento total del cabezal, tiempo promedio de espera en la cola de E/S,
 * solicitudes atendidas con cada política, suspensiones y reactivaciones, y el gráfico del desplazamiento acumulado.
 */
public class PanelMetricasDisco extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient Disco disco;
    private final transient Computador computador;
    private final JLabel lblDesplazamiento = new JLabel();
    private final JLabel lblEspera = new JLabel();
    private final JLabel lblAtendidas = new JLabel();
    private final JLabel lblSuspension = new JLabel();
    private final GraficoDesplazamiento grafico = new GraficoDesplazamiento();

    /** Arma el panel con las métricas del disco y de la suspensión del computador. */
    public PanelMetricasDisco(Disco disco, Computador computador) {
        this.disco = disco;
        this.computador = computador;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder("Métricas del disco y de la suspensión"));
        JPanel cifras = new JPanel(new GridLayout(4, 1));
        cifras.add(lblDesplazamiento);
        cifras.add(lblEspera);
        cifras.add(lblAtendidas);
        cifras.add(lblSuspension);
        add(cifras, BorderLayout.NORTH);
        add(grafico, BorderLayout.CENTER);
        refrescar();
    }

    /** Debe llamarse desde el hilo de Swing con el cerrojo del cluster tomado. */
    public void refrescar() {
        lblDesplazamiento.setText("Desplazamiento total del cabezal: " + disco.getDesplazamientoTotal() + " bloques");
        lblEspera.setText("Tiempo promedio de espera en la cola de E/S: "
                + String.format(Locale.US, "%.2f", disco.getEsperaPromedio()) + " ciclos");
        StringBuilder porPolitica = new StringBuilder();
        for (PoliticaDisco p : PoliticaDisco.values()) {
            porPolitica.append(porPolitica.length() == 0 ? "" : ", ").append(p).append(": ").append(disco.getAtendidas(p));
        }
        lblAtendidas.setText("Solicitudes atendidas: " + disco.getAtendidas() + "   (" + porPolitica + ")");
        lblSuspension.setText("Suspensiones: " + computador.getSuspensiones() + "   |   Reactivaciones: " + computador.getReactivaciones());
        grafico.mostrar(disco.copiarSerie());
    }
}

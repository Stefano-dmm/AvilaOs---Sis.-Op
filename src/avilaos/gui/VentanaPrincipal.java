package avilaos.gui;

import avilaos.estructuras.Nodo;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;

/**
 * Ventana principal. Solo observa y controla el Cluster: avanza el reloj y refresca paneles.
 * El Timer de Swing es un avance temporal del reloj hasta integrar el reloj global del proyecto.
 */
public class VentanaPrincipal extends JFrame {

    private final Cluster cluster;
    private final LogEventos log = new LogEventos();
    private final JLabel lblCiclo = new JLabel("Ciclo: 0");
    private final JButton btnIniciar = new JButton("Iniciar");
    private final JButton btnPaso = new JButton("Paso");
    private final JSpinner spnDuracionCiclo =
            new JSpinner(new SpinnerNumberModel(500, 50, 10000, 50));
    private final Timer reloj;
    private final JTabbedPane pestanas = new JTabbedPane();
    private final PanelComputador[] paneles;
    private int ciclo;

    public VentanaPrincipal(Cluster cluster) {
        super("AvilaOS - Simulador distribuido");
        this.cluster = cluster;
        this.paneles = new PanelComputador[cluster.getCantidadComputadores()];
        this.reloj = new Timer((Integer) spnDuracionCiclo.getValue(), e -> avanzarCiclo());

        construirPestanas();
        construirVentana();
        conectarControles();
    }

    private void construirPestanas() {
        int i = 0;
        Nodo<Computador> actual = cluster.getComputadores().getCabeza();
        while (actual != null) {
            paneles[i] = new PanelComputador(actual.getDato(), log);
            pestanas.addTab(actual.getDato().getNombre(), paneles[i]);
            actual = actual.getSiguiente();
            i++;
        }
    }

    private void construirVentana() {
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT));
        barra.add(btnIniciar);
        barra.add(btnPaso);
        barra.add(new JLabel("Duracion del ciclo (ms):"));
        barra.add(spnDuracionCiclo);
        barra.add(lblCiclo);

        JSplitPane division = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pestanas, log.getComponente());
        division.setResizeWeight(0.75);

        setLayout(new BorderLayout());
        add(barra, BorderLayout.NORTH);
        add(division, BorderLayout.CENTER);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 750);
        setLocationRelativeTo(null);
    }

    private void conectarControles() {
        btnIniciar.addActionListener(e -> alternarReloj());
        btnPaso.addActionListener(e -> {
            if (!reloj.isRunning()) {
                avanzarCiclo();
            }
        });
        spnDuracionCiclo.addChangeListener(e -> reloj.setDelay((Integer) spnDuracionCiclo.getValue()));
    }

    private void alternarReloj() {
        if (reloj.isRunning()) {
            reloj.stop();
            btnIniciar.setText("Iniciar");
            btnPaso.setEnabled(true);
            log.registrar("Simulacion en pausa");
        } else {
            reloj.start();
            btnIniciar.setText("Pausar");
            btnPaso.setEnabled(false);
            log.registrar("Simulacion en marcha");
        }
    }

    private void avanzarCiclo() {
        ciclo++;
        log.setCicloActual(ciclo);
        cluster.onTickGlobal(ciclo);
        lblCiclo.setText("Ciclo: " + ciclo);
        for (PanelComputador panel : paneles) {
            panel.refrescar();
        }
    }
}

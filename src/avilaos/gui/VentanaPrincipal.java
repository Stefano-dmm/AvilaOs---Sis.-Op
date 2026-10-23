package avilaos.gui;

import avilaos.config.ArchivoConfiguracion;
import avilaos.config.ConfigProceso;
import avilaos.config.Configuracion;
import avilaos.estructuras.Nodo;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
    private final Configuracion config;
    private final LogEventos log = new LogEventos();
    private final RegistradorEventos registrador;
    private final JLabel lblCiclo = new JLabel("Ciclo: 0");
    private final JButton btnIniciar = new JButton("Iniciar");
    private final JButton btnPaso = new JButton("Paso");
    private final JButton btnGuardar = new JButton("Guardar configuracion");
    private final JSpinner spnDuracionCiclo;
    private final Timer reloj;
    private final JTabbedPane pestanas = new JTabbedPane();
    private final PanelComputador[] paneles;
    private int ciclo;

    public VentanaPrincipal(Cluster cluster, Configuracion config) {
        super("AvilaOS - Simulador distribuido");
        this.cluster = cluster;
        this.config = config;
        this.registrador = new RegistradorEventos(cluster, log);
        this.paneles = new PanelComputador[cluster.getCantidadComputadores()];
        this.spnDuracionCiclo = new JSpinner(new SpinnerNumberModel(config.getCicloMs(),
                Configuracion.MIN_CICLO_MS, Configuracion.MAX_CICLO_MS, 50));
        this.reloj = new Timer(config.getCicloMs(), e -> avanzarCiclo());

        construirPestanas();
        construirVentana();
        conectarControles();
    }

    private void construirPestanas() {
        pestanas.addTab("Crear proceso", new PanelCrearProceso(cluster, log, this::procesoCreado));
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
        barra.add(btnGuardar);

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
        btnGuardar.addActionListener(e -> guardarConfiguracion());
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
        registrador.observarCiclo();
        lblCiclo.setText("Ciclo: " + ciclo);
        refrescarPaneles();
    }

    private void procesoCreado(ConfigProceso proceso) {
        config.agregarProceso(proceso);
        refrescarPaneles();
    }

    private void guardarConfiguracion() {
        JFileChooser selector = new JFileChooser();
        selector.setSelectedFile(new File("configuracion.csv"));
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            config.setCicloMs((Integer) spnDuracionCiclo.getValue());
            ArchivoConfiguracion.guardar(selector.getSelectedFile().toPath(), config);
            log.registrar("Configuracion guardada en " + selector.getSelectedFile().getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar el archivo: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refrescarPaneles() {
        for (PanelComputador panel : paneles) {
            panel.refrescar();
        }
    }
}

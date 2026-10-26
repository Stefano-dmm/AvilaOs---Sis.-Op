package avilaos.gui;

import avilaos.config.ArchivoConfiguracion;
import avilaos.config.ConfigProceso;
import avilaos.config.Configuracion;
import avilaos.estructuras.Nodo;
import avilaos.hardware.RelojGlobal;
import avilaos.metricas.HistorialUtilizacion;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
import javax.swing.SwingUtilities;

/**
 * Ventana principal. Solo observa y controla el Cluster: el reloj global (con sus hilos) hace
 * avanzar la simulacion y la ventana refresca lo que ve al terminar cada ciclo.
 */
public class VentanaPrincipal extends JFrame {

    private final Cluster cluster;
    private final Configuracion config;
    private final LogEventos log = new LogEventos();
    private final RegistradorEventos registrador;
    private final HistorialUtilizacion historial;
    private PanelMetricas panelMetricas;
    private final JLabel lblCiclo = new JLabel("Ciclo: 0");
    private final JButton btnIniciar = new JButton("Iniciar");
    private final JButton btnPaso = new JButton("Paso");
    private final JButton btnGuardar = new JButton("Guardar configuracion");
    private final JSpinner spnDuracionCiclo;
    private final RelojGlobal reloj;
    private final JTabbedPane pestanas = new JTabbedPane();
    private final PanelComputador[] paneles;

    public VentanaPrincipal(Cluster cluster, Configuracion config) {
        super("AvilaOS - Simulador distribuido");
        this.cluster = cluster;
        this.config = config;
        this.registrador = new RegistradorEventos(cluster, log);
        this.historial = new HistorialUtilizacion(cluster);
        this.paneles = new PanelComputador[cluster.getCantidadComputadores()];
        this.spnDuracionCiclo = new JSpinner(new SpinnerNumberModel(config.getCicloMs(),
                Configuracion.MIN_CICLO_MS, Configuracion.MAX_CICLO_MS, 50));
        this.reloj = cluster.crearReloj(config.getCicloMs());
        cluster.setOyenteEventos(log::registrar);
        reloj.setAlCompletarCiclo(this::alCompletarCiclo);
        reloj.setAlFallar(this::alFallarElReloj);

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
        panelMetricas = new PanelMetricas(cluster, historial);
        pestanas.addTab("Metricas", panelMetricas);
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
        btnPaso.addActionListener(e -> reloj.paso());
        btnGuardar.addActionListener(e -> guardarConfiguracion());
        spnDuracionCiclo.addChangeListener(e -> reloj.setVelocidadMs((Integer) spnDuracionCiclo.getValue()));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                reloj.detener();
            }
        });
    }

    private void alternarReloj() {
        if (reloj.isEnMarcha()) {
            reloj.pausar();
            btnIniciar.setText("Iniciar");
            btnPaso.setEnabled(true);
            log.registrar("Simulacion en pausa");
        } else {
            reloj.iniciar();
            btnIniciar.setText("Pausar");
            btnPaso.setEnabled(false);
            log.registrar("Simulacion en marcha");
        }
    }

    /** Se ejecuta en el hilo del reloj, con el cerrojo del cluster tomado: nadie modifica el estado. */
    private void alCompletarCiclo(long cicloReloj) {
        int ciclo = (int) cicloReloj;
        log.setCicloActual(ciclo);
        registrador.observarCiclo();
        historial.registrar();
        SwingUtilities.invokeLater(() -> {
            lblCiclo.setText("Ciclo: " + ciclo);
            refrescarPaneles();
        });
    }

    private void alFallarElReloj(Throwable error) {
        SwingUtilities.invokeLater(() -> {
            btnIniciar.setText("Iniciar");
            btnPaso.setEnabled(true);
            JOptionPane.showMessageDialog(this, "La simulacion se detuvo por un error interno: " + error,
                    "Error", JOptionPane.ERROR_MESSAGE);
        });
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

    /** Lee el estado del cluster, por eso toma el cerrojo para no ver un ciclo a medias. */
    private void refrescarPaneles() {
        cluster.adquirir();
        try {
            for (PanelComputador panel : paneles) {
                panel.refrescar();
            }
            panelMetricas.refrescar();
        } finally {
            cluster.liberar();
        }
    }
}

package avilaos.gui;

import avilaos.archivos.ModoUsuario;
import avilaos.archivos.NodoArchivo;
import avilaos.archivos.Sesion;
import avilaos.hardware.RelojGlobal;
import avilaos.json.Json;
import avilaos.metricas.HistorialMetricas;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.nucleo.SistemaAvilaOS;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
 * Ventana principal del Proyecto 2. Solo observa y controla el sistema: el reloj global hace avanzar la simulación y la
 * ventana refresca al terminar cada ciclo. Tiene dos modos: el administrador lo puede todo; el usuario solo opera sobre
 * archivos (con las reglas de permisos) y no toca las políticas, el disco ni las métricas.
 */
public class VentanaPrincipalP2 extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final String MODO_ADMIN = "Administrador";
    private static final String MODO_USUARIO = "Usuario";
    private static final int PESTANA_ARCHIVOS = 0;
    private static final int PESTANA_ASIGNACION = 1;

    private final transient SistemaAvilaOS sistema;
    private final transient Cluster cluster;
    private final transient LogEventos log = new LogEventos();
    private final transient HistorialMetricas historial;
    private final transient RelojGlobal reloj;
    private final JTabbedPane pestanas = new JTabbedPane();
    private final JLabel lblCiclo = new JLabel("Ciclo: 0");
    private final JButton btnIniciar = new JButton("Iniciar");
    private final JButton btnPaso = new JButton("Paso");
    private final JButton btnGuardar = new JButton("Guardar sistema de archivos");
    private final JSpinner spnCiclo;
    private final JComboBox<String> cmbModo = new JComboBox<>(new String[] {MODO_ADMIN, MODO_USUARIO});
    private final JComboBox<String> cmbUsuario = new JComboBox<>();
    private PanelSistemaArchivos panelArchivos;
    private PanelTablaAsignacion panelAsignacion;
    private PanelDisco panelDisco;
    private PanelComputador panelComputador;
    private PanelMetricas panelMetricas;
    private PanelMetricasDisco panelMetricasDisco;
    /** Sesión actual; la escribe el hilo de Swing y la leen los paneles desde ahí mismo. */
    private volatile Sesion sesion = Sesion.administrador();

    /** Arma la ventana, crea el reloj global del cluster y lo conecta con la interfaz. */
    public VentanaPrincipalP2(SistemaAvilaOS sistema, int cicloMs) {
        super("ÁvilaOS - Disco, sistema de archivos y suspensión de procesos");
        this.sistema = sistema;
        this.cluster = sistema.getCluster();
        this.historial = new HistorialMetricas(cluster);
        this.spnCiclo = new JSpinner(new SpinnerNumberModel(Math.max(cicloMs, 1), 1, 5000, 50));
        this.reloj = cluster.crearReloj(cicloMs);
        cluster.setOyenteEventos(mensaje -> {
            log.setCicloActual((int) reloj.getCicloActual());
            log.registrar(mensaje);
        });
        reloj.setAlCompletarCiclo(this::alCompletarCiclo);
        reloj.setAlFallar(this::alFallarElReloj);

        cargarUsuarios();
        construirPestanas();
        construirVentana();
        conectarControles();
        aplicarModo();
    }

    /** Los usuarios que se pueden elegir son los dueños que aparecen en el sistema de archivos cargado. */
    private void cargarUsuarios() {
        NodoArchivo[] nodos = sistema.getArchivos().todosLosArchivos();
        for (NodoArchivo nodo : nodos) {
            String dueno = nodo.getDueno();
            if (dueno == null || Sesion.ADMIN.equals(dueno) || yaEsta(dueno)) {
                continue;
            }
            cmbUsuario.addItem(dueno);
        }
        if (cmbUsuario.getItemCount() == 0) {
            cmbUsuario.addItem("user1");
        }
    }

    private boolean yaEsta(String nombre) {
        for (int i = 0; i < cmbUsuario.getItemCount(); i++) {
            if (cmbUsuario.getItemAt(i).equals(nombre)) {
                return true;
            }
        }
        return false;
    }

    private void construirPestanas() {
        Computador computador = sistema.getComputador();
        panelArchivos = new PanelSistemaArchivos(sistema, () -> sesion, log, this::refrescarPaneles);
        panelAsignacion = new PanelTablaAsignacion(sistema.getArchivos());
        panelDisco = new PanelDisco(sistema, log);
        panelComputador = new PanelComputador(computador, log);
        panelMetricas = new PanelMetricas(cluster, historial);
        panelMetricasDisco = new PanelMetricasDisco(sistema.getDisco(), computador);
        pestanas.addTab("Sistema de archivos", panelArchivos);
        pestanas.addTab("Tabla de asignación", panelAsignacion);
        pestanas.addTab("Disco", panelDisco);
        pestanas.addTab(computador.getNombre(), panelComputador);
        pestanas.addTab("Métricas CPU", panelMetricas);
        pestanas.addTab("Métricas disco", panelMetricasDisco);
    }

    private void construirVentana() {
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT));
        barra.add(btnIniciar);
        barra.add(btnPaso);
        barra.add(new JLabel("Duración del ciclo (ms):"));
        barra.add(spnCiclo);
        barra.add(lblCiclo);
        barra.add(new JLabel("Modo:"));
        barra.add(cmbModo);
        barra.add(new JLabel("Usuario:"));
        barra.add(cmbUsuario);
        barra.add(btnGuardar);

        JSplitPane division = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pestanas, log.getComponente());
        division.setResizeWeight(0.78);
        setLayout(new BorderLayout());
        add(barra, BorderLayout.NORTH);
        add(division, BorderLayout.CENTER);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1150, 800);
        setLocationRelativeTo(null);
    }

    private void conectarControles() {
        btnIniciar.addActionListener(e -> alternarReloj());
        btnPaso.addActionListener(e -> reloj.paso());
        btnGuardar.addActionListener(e -> guardarSistemaArchivos());
        spnCiclo.addChangeListener(e -> reloj.setVelocidadMs((Integer) spnCiclo.getValue()));
        cmbModo.addActionListener(e -> aplicarModo());
        cmbUsuario.addActionListener(e -> aplicarModo());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                reloj.detener();
            }
        });
    }

    /** Cambia la sesión y bloquea lo que el usuario normal no puede tocar (políticas, disco, métricas y guardado). */
    private void aplicarModo() {
        boolean admin = MODO_ADMIN.equals(cmbModo.getSelectedItem());
        cmbUsuario.setEnabled(!admin);
        String nombre = (String) cmbUsuario.getSelectedItem();
        sesion = admin || nombre == null ? Sesion.administrador() : Sesion.usuario(nombre);
        panelDisco.setControlesHabilitados(admin);
        panelComputador.setControlesHabilitados(admin);
        btnGuardar.setEnabled(admin);
        for (int i = 0; i < pestanas.getTabCount(); i++) {
            pestanas.setEnabledAt(i, admin || i == PESTANA_ARCHIVOS || i == PESTANA_ASIGNACION);
        }
        if (!pestanas.isEnabledAt(pestanas.getSelectedIndex())) {
            pestanas.setSelectedIndex(PESTANA_ARCHIVOS);
        }
        ModoUsuario modo = sesion.modo();
        log.registrar("Modo " + (modo == ModoUsuario.ADMINISTRADOR ? "administrador" : "usuario " + sesion.usuario()));
    }

    private void alternarReloj() {
        if (reloj.isEnMarcha()) {
            reloj.pausar();
            btnIniciar.setText("Iniciar");
            btnPaso.setEnabled(true);
            log.registrar("Simulación en pausa");
        } else {
            reloj.iniciar();
            btnIniciar.setText("Pausar");
            btnPaso.setEnabled(false);
            log.registrar("Simulación en marcha");
        }
    }

    /** Se ejecuta en el hilo del reloj con el cerrojo del cluster tomado: nadie modifica el estado. */
    private void alCompletarCiclo(long cicloReloj) {
        int ciclo = (int) cicloReloj;
        log.setCicloActual(ciclo);
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
            JOptionPane.showMessageDialog(this, "La simulación se detuvo por un error interno: " + error,
                    "Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    /** Guarda el estado actual del sistema de archivos en un JSON con el mismo formato que se carga. */
    private void guardarSistemaArchivos() {
        JFileChooser selector = new JFileChooser();
        selector.setSelectedFile(new File("sistema_archivos_guardado.json"));
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String texto;
        cluster.adquirir();
        try {
            texto = Json.escribir(sistema.getArchivos().aJson());
        } finally {
            cluster.liberar();
        }
        try {
            Files.writeString(selector.getSelectedFile().toPath(), texto, StandardCharsets.UTF_8);
            log.registrar("Sistema de archivos guardado en " + selector.getSelectedFile().getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar el archivo: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Lee el estado del sistema, por eso toma el cerrojo para no ver un ciclo a medias. */
    private void refrescarPaneles() {
        cluster.adquirir();
        try {
            panelArchivos.refrescar();
            panelAsignacion.refrescar();
            panelDisco.refrescar();
            panelComputador.refrescar();
            panelMetricas.refrescar();
            panelMetricasDisco.refrescar();
        } finally {
            cluster.liberar();
        }
    }
}

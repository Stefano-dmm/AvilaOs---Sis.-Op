package avilaos.gui;

import avilaos.archivos.DefinicionSistemaArchivos;
import avilaos.config.DefinicionProcesos;
import avilaos.nucleo.SistemaAvilaOS;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Ventana de carga: ÁvilaOS exige los dos JSON (sistema de archivos y procesos) antes de arrancar el reloj. Cada archivo
 * se valida al elegirlo y los errores se muestran aquí, sin cerrar la ventana ni tumbar el programa.
 */
public class VentanaCargaP2 extends JFrame {

    private static final long serialVersionUID = 1L;

    private final JTextField txtArchivos = new JTextField(34);
    private final JTextField txtProcesos = new JTextField(34);
    private final JTextArea estado = new JTextArea(9, 50);
    private final JButton btnIniciar = new JButton("Iniciar ÁvilaOS");
    private transient DefinicionSistemaArchivos definicionArchivos;
    private transient DefinicionProcesos definicionProcesos;
    private File carpeta = new File(System.getProperty("user.home"), "Downloads");

    /** Arma la ventana de carga; el botón de iniciar queda bloqueado hasta que los dos JSON sean válidos. */
    public VentanaCargaP2() {
        super("ÁvilaOS - Carga del sistema de archivos y de los procesos");
        txtArchivos.setEditable(false);
        txtProcesos.setEditable(false);
        estado.setEditable(false);
        estado.setLineWrap(true);
        estado.setWrapStyleWord(true);
        btnIniciar.setEnabled(false);

        JPanel filas = new JPanel(new GridBagLayout());
        filas.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridy = 0;
        c.gridx = 0;
        filas.add(new JLabel("1. JSON del sistema de archivos:"), c);
        c.gridx = 1;
        filas.add(txtArchivos, c);
        JButton btnArchivos = new JButton("Examinar...");
        btnArchivos.addActionListener(e -> elegir(true));
        c.gridx = 2;
        filas.add(btnArchivos, c);
        c.gridy = 1;
        c.gridx = 0;
        filas.add(new JLabel("2. JSON de procesos y configuración:"), c);
        c.gridx = 1;
        filas.add(txtProcesos, c);
        JButton btnProcesos = new JButton("Examinar...");
        btnProcesos.addActionListener(e -> elegir(false));
        c.gridx = 2;
        filas.add(btnProcesos, c);

        JScrollPane scroll = new JScrollPane(estado);
        scroll.setBorder(BorderFactory.createTitledBorder("Estado de la carga"));
        JPanel sur = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnIniciar.addActionListener(e -> iniciar());
        sur.add(btnIniciar);

        setLayout(new BorderLayout(6, 6));
        add(filas, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(sur, BorderLayout.SOUTH);
        estado.setText("Cargue los dos archivos JSON. El reloj no arranca hasta que ambos sean válidos.\n"
                + "Si el disco no tiene los archivos, los procesos de E/S fallarían al buscar sus rutas.");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
    }

    /** Pide un archivo y lo valida; un error se muestra en rojo y deja el botón bloqueado. */
    private void elegir(boolean sistemaDeArchivos) {
        JFileChooser selector = new JFileChooser(carpeta);
        selector.setFileFilter(new FileNameExtensionFilter("Archivos JSON", "json"));
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File archivo = selector.getSelectedFile();
        carpeta = archivo.getParentFile() == null ? carpeta : archivo.getParentFile();
        try {
            String texto = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
            if (sistemaDeArchivos) {
                definicionArchivos = DefinicionSistemaArchivos.desdeJson(texto);
                txtArchivos.setText(archivo.getAbsolutePath());
                mostrar("Sistema de archivos válido: disco de " + definicionArchivos.bloquesTotales() + " bloques, cabezal en "
                        + definicionArchivos.cabezalInicial() + ", " + definicionArchivos.entradas().tamanio() + " elementos.", false);
            } else {
                definicionProcesos = DefinicionProcesos.desdeJson(texto);
                txtProcesos.setText(archivo.getAbsolutePath());
                mostrar("Procesos válidos: RAM " + definicionProcesos.ram() + ", CPU " + definicionProcesos.politica() + ", disco "
                        + definicionProcesos.politicaDisco() + ", swap " + definicionProcesos.bloquesSwap() + ", "
                        + definicionProcesos.procesos().getTamanio() + " procesos.", false);
            }
        } catch (IOException ex) {
            mostrar("No se pudo leer " + archivo.getName() + ": " + ex.getMessage(), true);
            descartar(sistemaDeArchivos);
        } catch (IllegalArgumentException ex) {
            mostrar(archivo.getName() + " no es válido: " + ex.getMessage(), true);
            descartar(sistemaDeArchivos);
        }
        btnIniciar.setEnabled(definicionArchivos != null && definicionProcesos != null);
    }

    private void descartar(boolean sistemaDeArchivos) {
        if (sistemaDeArchivos) {
            definicionArchivos = null;
            txtArchivos.setText("");
        } else {
            definicionProcesos = null;
            txtProcesos.setText("");
        }
    }

    private void mostrar(String texto, boolean error) {
        estado.setForeground(error ? new Color(176, 0, 32) : Color.BLACK);
        estado.setText(texto);
    }

    /** Arma el sistema con los dos JSON (validando que encajen entre sí) y abre la ventana principal. */
    private void iniciar() {
        try {
            SistemaAvilaOS sistema = SistemaAvilaOS.construir(definicionArchivos, definicionProcesos);
            sistema.lanzarProcesos(definicionProcesos);
            VentanaPrincipalP2 principal = new VentanaPrincipalP2(sistema, definicionProcesos.cicloMs());
            dispose();
            SwingUtilities.invokeLater(() -> principal.setVisible(true));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            mostrar("No se pudo iniciar: " + ex.getMessage(), true);
        }
    }
}

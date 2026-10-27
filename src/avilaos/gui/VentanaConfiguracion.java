package avilaos.gui;

import avilaos.config.ArchivoConfiguracion;
import avilaos.config.ConfigBuffer;
import avilaos.config.ConfigComputador;
import avilaos.config.Configuracion;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.config.ConfigProceso;
import avilaos.modelo.PoliticaPlanificacion;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/** Vista de configuracion inicial: se ve antes de arrancar la simulacion. */
public class VentanaConfiguracion extends JFrame {

    private final Consumer<Configuracion> alIniciar;
    private final JSpinner spnCiclo = new JSpinner(new SpinnerNumberModel(500,
            Configuracion.MIN_CICLO_MS, Configuracion.MAX_CICLO_MS, 50));
    private final JSpinner spnLatencia = new JSpinner(new SpinnerNumberModel(2,
            Configuracion.MIN_LATENCIA, Configuracion.MAX_LATENCIA, 1));
    private final JSpinner spnCantidad = new JSpinner(new SpinnerNumberModel(2,
            Configuracion.MIN_COMPUTADORES, Configuracion.MAX_COMPUTADORES, 1));
    private final FilaComputador[] filas = new FilaComputador[Configuracion.MAX_COMPUTADORES];
    private final JLabel lblProcesos = new JLabel();
    private ListaEnlazada<ConfigProceso> procesosCargados = new ListaEnlazada<>();
    private ListaEnlazada<ConfigBuffer> buffersCargados = new ListaEnlazada<>();

    /** Arma la ventana con los valores iniciales dados. */
    public VentanaConfiguracion(Configuracion inicial, Consumer<Configuracion> alIniciar) {
        super("AvilaOS - Configuracion");
        this.alIniciar = alIniciar;
        construir();
        aplicar(inicial);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
    }

    /** Distribuye los campos generales, las filas de computadores y los botones. */
    private void construir() {
        JPanel general = new JPanel(new GridLayout(3, 2, 6, 6));
        general.setBorder(BorderFactory.createTitledBorder("General"));
        general.add(new JLabel("Duracion del ciclo (ms):"));
        general.add(spnCiclo);
        general.add(new JLabel("Latencia de red (ciclos):"));
        general.add(spnLatencia);
        general.add(new JLabel("Cantidad de computadores:"));
        general.add(spnCantidad);

        JPanel listaPcs = new JPanel(new GridLayout(Configuracion.MAX_COMPUTADORES, 1, 4, 4));
        listaPcs.setBorder(BorderFactory.createTitledBorder("Computadores"));
        for (int i = 0; i < filas.length; i++) {
            filas[i] = new FilaComputador(i);
            listaPcs.add(filas[i]);
        }
        spnCantidad.addChangeListener(e -> mostrarFilas());

        JButton btnCargar = new JButton("Cargar archivo...");
        JButton btnGuardar = new JButton("Guardar archivo...");
        JButton btnIniciar = new JButton("Iniciar simulacion");
        btnCargar.addActionListener(e -> cargarArchivo());
        btnGuardar.addActionListener(e -> guardarArchivo());
        btnIniciar.addActionListener(e -> iniciar());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(lblProcesos);
        botones.add(btnCargar);
        botones.add(btnGuardar);
        botones.add(btnIniciar);

        JPanel centro = new JPanel(new BorderLayout(8, 8));
        centro.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        centro.add(general, BorderLayout.NORTH);
        centro.add(listaPcs, BorderLayout.CENTER);
        centro.add(botones, BorderLayout.SOUTH);
        setContentPane(centro);
    }

    /** Muestra solo tantas filas de computadores como indique el selector de cantidad. */
    private void mostrarFilas() {
        int cantidad = (Integer) spnCantidad.getValue();
        for (int i = 0; i < filas.length; i++) {
            filas[i].setVisible(i < cantidad);
        }
        revalidate();
        pack();
    }

    /** Vuelca una configuracion (por ejemplo la de un archivo) en los campos. */
    private void aplicar(Configuracion config) {
        spnCiclo.setValue(config.getCicloMs());
        spnLatencia.setValue(config.getLatenciaRed());
        spnCantidad.setValue(config.getComputadores().getTamanio());
        int i = 0;
        Nodo<ConfigComputador> actual = config.getComputadores().getCabeza();
        while (actual != null && i < filas.length) {
            filas[i].mostrar(actual.getDato());
            actual = actual.getSiguiente();
            i++;
        }
        procesosCargados = config.getProcesos();
        buffersCargados = config.getBuffers();
        lblProcesos.setText("Buffers iniciales: " + buffersCargados.getTamanio()
                + "  |  Procesos iniciales: " + procesosCargados.getTamanio());
        mostrarFilas();
    }

    /** Arma la configuracion desde el formulario; lanza IllegalArgumentException si algo es invalido. */
    private Configuracion leerFormulario() {
        Configuracion config = new Configuracion();
        config.setCicloMs((Integer) spnCiclo.getValue());
        config.setLatenciaRed((Integer) spnLatencia.getValue());
        int cantidad = (Integer) spnCantidad.getValue();
        for (int i = 0; i < cantidad; i++) {
            config.agregarComputador(filas[i].leer());
        }
        Nodo<ConfigBuffer> b = buffersCargados.getCabeza();
        while (b != null) {
            config.agregarBuffer(b.getDato());
            b = b.getSiguiente();
        }
        Nodo<ConfigProceso> p = procesosCargados.getCabeza();
        while (p != null) {
            config.agregarProceso(p.getDato());
            p = p.getSiguiente();
        }
        config.validarCompleta();
        return config;
    }

    /** Valida el formulario completo y, si esta bien, cierra la ventana y arranca la simulacion. */
    private void iniciar() {
        try {
            Configuracion config = leerFormulario();
            dispose();
            alIniciar.accept(config);
        } catch (IllegalArgumentException ex) {
            avisar(ex.getMessage());
        }
    }

    /** Valida el formulario y lo guarda en un archivo CSV elegido por el usuario. */
    private void guardarArchivo() {
        try {
            Configuracion config = leerFormulario();
            JFileChooser selector = new JFileChooser();
            selector.setSelectedFile(new File("configuracion.csv"));
            if (selector.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                ArchivoConfiguracion.guardar(selector.getSelectedFile().toPath(), config);
                JOptionPane.showMessageDialog(this, "Configuracion guardada.");
            }
        } catch (IllegalArgumentException ex) {
            avisar(ex.getMessage());
        } catch (IOException ex) {
            avisar("No se pudo guardar el archivo: " + ex.getMessage());
        }
    }

    /** Carga un CSV; si es invalido muestra el numero de linea del error. */
    private void cargarArchivo() {
        JFileChooser selector = new JFileChooser();
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            aplicar(ArchivoConfiguracion.cargar(selector.getSelectedFile().toPath()));
        } catch (IllegalArgumentException ex) {
            avisar("Archivo invalido. " + ex.getMessage());
        } catch (IOException ex) {
            avisar("No se pudo leer el archivo: " + ex.getMessage());
        }
    }

    /** Muestra un aviso de dato invalido sin cerrar la aplicacion. */
    private void avisar(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Datos invalidos", JOptionPane.WARNING_MESSAGE);
    }

    /** Una fila del formulario: nombre, RAM, politica inicial y quantum de un computador. */
    private static class FilaComputador extends JPanel {

        private final JTextField txtNombre;
        private final JSpinner spnRam = new JSpinner(new SpinnerNumberModel(512,
                Configuracion.MIN_RAM, Configuracion.MAX_RAM, 64));
        private final JComboBox<PoliticaPlanificacion> cmbPolitica =
                new JComboBox<>(PoliticaPlanificacion.values());
        private final JSpinner spnQuantum = new JSpinner(new SpinnerNumberModel(3,
                Configuracion.MIN_QUANTUM, Configuracion.MAX_QUANTUM, 1));

        FilaComputador(int indice) {
            super(new FlowLayout(FlowLayout.LEFT, 6, 2));
            txtNombre = new JTextField("Nodo-" + (indice + 1), 10);
            add(new JLabel("PC" + (indice + 1) + " nombre:"));
            add(txtNombre);
            add(new JLabel("RAM:"));
            add(spnRam);
            add(new JLabel("Politica:"));
            add(cmbPolitica);
            add(new JLabel("Quantum:"));
            add(spnQuantum);
        }

        void mostrar(ConfigComputador c) {
            txtNombre.setText(c.nombre());
            spnRam.setValue(c.ram());
            cmbPolitica.setSelectedItem(c.politica());
            spnQuantum.setValue(c.quantum());
        }

        ConfigComputador leer() {
            return new ConfigComputador(txtNombre.getText().trim(), (Integer) spnRam.getValue(),
                    (PoliticaPlanificacion) cmbPolitica.getSelectedItem(), (Integer) spnQuantum.getValue());
        }
    }
}

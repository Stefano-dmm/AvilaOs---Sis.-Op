package avilaos.gui;

import avilaos.config.ConfigProceso;
import avilaos.config.Configuracion;
import avilaos.config.FabricaProcesos;
import avilaos.config.LectorFormulario;
import avilaos.estructuras.Nodo;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.sincronizacion.BufferSO;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Vista 1: formulario para crear procesos. Pide solo los datos que corresponden al tipo elegido y
 * deja que el usuario escoja el computador o que AvilaOS lo asigne por balanceo de carga.
 */
public class PanelCrearProceso extends JPanel {

    private final Cluster cluster;
    private final Configuracion config;
    private final LogEventos log;
    private final Runnable alCrear;

    private final JTextField txtNombre = new JTextField(15);
    private final JComboBox<TipoProceso> cmbTipo = new JComboBox<>(TipoProceso.values());
    private final JTextField txtPrioridad = new JTextField("1", 8);
    private final JTextField txtMemoria = new JTextField(8);
    private final JTextField txtDeadline = new JTextField(8);
    private final JComboBox<String> cmbComputador = new JComboBox<>();

    private final JLabel lblInstrucciones = new JLabel("Instrucciones (ciclos de CPU):");
    private final JTextField txtInstrucciones = new JTextField(8);
    private final JLabel lblIntervalo = new JLabel();
    private final JTextField txtIntervalo = new JTextField(8);
    private final JLabel lblElementos = new JLabel();
    private final JTextField txtElementos = new JTextField(8);
    private final JLabel lblCiclosEs = new JLabel("Ciclos que dura cada E/S:");
    private final JTextField txtCiclosEs = new JTextField(8);
    private final JLabel lblBuffer = new JLabel("Buffer:");
    private final JComboBox<String> cmbBuffer = new JComboBox<>();

    public PanelCrearProceso(Cluster cluster, Configuracion config, LogEventos log, Runnable alCrear) {
        this.cluster = cluster;
        this.config = config;
        this.log = log;
        this.alCrear = alCrear;
        setLayout(new BorderLayout());
        add(construirFormulario(), BorderLayout.NORTH);
        cargarComputadores();
        recargarBuffers();
        cmbTipo.addActionListener(e -> actualizarCampos());
        actualizarCampos();
    }

    private JPanel construirFormulario() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Nuevo proceso"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        agregarFila(form, c, 0, new JLabel("Nombre:"), txtNombre);
        agregarFila(form, c, 1, new JLabel("Tipo:"), cmbTipo);
        agregarFila(form, c, 2, new JLabel("Prioridad (mayor = mas importante):"), txtPrioridad);
        agregarFila(form, c, 3, new JLabel("Memoria requerida:"), txtMemoria);
        agregarFila(form, c, 4, new JLabel("Deadline (ciclos):"), txtDeadline);
        agregarFila(form, c, 5, new JLabel("Computador:"), cmbComputador);
        agregarFila(form, c, 6, lblInstrucciones, txtInstrucciones);
        agregarFila(form, c, 7, lblIntervalo, txtIntervalo);
        agregarFila(form, c, 8, lblElementos, txtElementos);
        agregarFila(form, c, 9, lblCiclosEs, txtCiclosEs);
        agregarFila(form, c, 10, lblBuffer, cmbBuffer);

        JButton btnCrear = new JButton("Crear proceso");
        btnCrear.addActionListener(e -> crear());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        botones.add(btnCrear);
        c.gridx = 1;
        c.gridy = 11;
        form.add(botones, c);
        return form;
    }

    private static void agregarFila(JPanel form, GridBagConstraints c, int fila, JLabel etiqueta, Component campo) {
        c.gridx = 0;
        c.gridy = fila;
        form.add(etiqueta, c);
        c.gridx = 1;
        form.add(campo, c);
    }

    /** Muestra solo los campos que necesita el tipo elegido. */
    private void actualizarCampos() {
        TipoProceso tipo = (TipoProceso) cmbTipo.getSelectedItem();
        boolean cpu = tipo == TipoProceso.CPU_BOUND;
        boolean es = tipo == TipoProceso.IO_BOUND;
        boolean comunicante = tipo == TipoProceso.PRODUCTOR || tipo == TipoProceso.CONSUMIDOR;

        mostrar(lblInstrucciones, txtInstrucciones, cpu || es);
        mostrar(lblIntervalo, txtIntervalo, es || comunicante);
        mostrar(lblElementos, txtElementos, comunicante);
        mostrar(lblCiclosEs, txtCiclosEs, es);
        mostrar(lblBuffer, cmbBuffer, comunicante);

        lblIntervalo.setText(es ? "Ciclos de CPU entre cada E/S:" : "Ciclos para producir/consumir un elemento:");
        lblElementos.setText(tipo == TipoProceso.PRODUCTOR
                ? "Elementos que debe producir:" : "Elementos que debe consumir:");
        revalidate();
        repaint();
    }

    private static void mostrar(JLabel etiqueta, Component campo, boolean visible) {
        etiqueta.setVisible(visible);
        campo.setVisible(visible);
    }

    private void cargarComputadores() {
        cmbComputador.addItem("Automatico (balanceo de carga)");
        Nodo<Computador> actual = cluster.getComputadores().getCabeza();
        while (actual != null) {
            cmbComputador.addItem("PC" + actual.getDato().getId() + " - " + actual.getDato().getNombre());
            actual = actual.getSiguiente();
        }
    }

    /** Vuelve a leer los buffers del cluster; se llama cuando se crea uno nuevo. */
    public void recargarBuffers() {
        int seleccionado = cmbBuffer.getSelectedIndex();
        cmbBuffer.removeAllItems();
        Nodo<BufferSO> actual = cluster.getBuffers().getCabeza();
        while (actual != null) {
            BufferSO b = actual.getDato();
            cmbBuffer.addItem(b.getNombre() + " (en " + b.getAnfitrion().getNombre()
                    + ", capacidad " + b.getCapacidad() + ")");
            actual = actual.getSiguiente();
        }
        if (seleccionado >= 0 && seleccionado < cmbBuffer.getItemCount()) {
            cmbBuffer.setSelectedIndex(seleccionado);
        }
    }

    private void crear() {
        try {
            int indiceComputador = cmbComputador.getSelectedIndex() - 1;
            ConfigProceso datos = LectorFormulario.proceso(txtNombre.getText(),
                    (TipoProceso) cmbTipo.getSelectedItem(), txtPrioridad.getText(), txtMemoria.getText(),
                    txtDeadline.getText(), txtInstrucciones.getText(), txtIntervalo.getText(),
                    txtElementos.getText(), txtCiclosEs.getText(), indiceComputador,
                    cmbBuffer.getSelectedIndex());
            Proceso proceso = FabricaProcesos.crear(datos, cluster);
            config.agregarProceso(datos);

            cluster.adquirir();
            try {
                if (indiceComputador >= 0) {
                    Computador destino = cluster.obtenerPorIndice(indiceComputador);
                    cluster.asignarProcesoManual(proceso, destino.getId());
                    log.registrar("Se crea " + proceso.getEtiqueta() + " en " + destino.getNombre());
                } else {
                    cluster.asignarProcesoAutomatico(proceso);
                }
            } finally {
                cluster.liberar();
            }
            limpiar();
            alCrear.run();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Datos invalidos",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void limpiar() {
        txtNombre.setText("");
        txtMemoria.setText("");
        txtDeadline.setText("");
        txtInstrucciones.setText("");
        txtIntervalo.setText("");
        txtElementos.setText("");
        txtCiclosEs.setText("");
        txtNombre.requestFocusInWindow();
    }
}

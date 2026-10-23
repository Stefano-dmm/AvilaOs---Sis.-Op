package avilaos.gui;

import avilaos.config.ConfigProceso;
import avilaos.estructuras.Nodo;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Vista 1: formulario para crear procesos y asignarlos manualmente a un computador. */
public class PanelCrearProceso extends JPanel {

    private final Cluster cluster;
    private final LogEventos log;
    private final Consumer<ConfigProceso> alCrear;

    private final JTextField txtNombre = new JTextField(15);
    private final JComboBox<TipoProceso> cmbTipo = new JComboBox<>(TipoProceso.values());
    private final JTextField txtInstrucciones = new JTextField(8);
    private final JTextField txtMemoria = new JTextField(8);
    private final JTextField txtPrioridad = new JTextField("1", 8);
    private final JTextField txtDeadline = new JTextField(8);
    private final JComboBox<Computador> cmbComputador = new JComboBox<>();

    public PanelCrearProceso(Cluster cluster, LogEventos log, Consumer<ConfigProceso> alCrear) {
        this.cluster = cluster;
        this.log = log;
        this.alCrear = alCrear;
        setLayout(new BorderLayout());
        add(construirFormulario(), BorderLayout.NORTH);
        cargarComputadores();
    }

    private JPanel construirFormulario() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Nuevo proceso"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        agregarFila(form, c, 0, "Nombre:", txtNombre);
        agregarFila(form, c, 1, "Tipo:", cmbTipo);
        agregarFila(form, c, 2, "Instrucciones (ciclos):", txtInstrucciones);
        agregarFila(form, c, 3, "Memoria requerida:", txtMemoria);
        agregarFila(form, c, 4, "Prioridad (mayor = mas importante):", txtPrioridad);
        agregarFila(form, c, 5, "Deadline (ciclos):", txtDeadline);
        agregarFila(form, c, 6, "Computador:", cmbComputador);

        JButton btnCrear = new JButton("Crear proceso");
        btnCrear.addActionListener(e -> crear());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        botones.add(btnCrear);
        c.gridx = 1;
        c.gridy = 7;
        form.add(botones, c);
        return form;
    }

    private static void agregarFila(JPanel form, GridBagConstraints c, int fila,
                                    String etiqueta, java.awt.Component campo) {
        c.gridx = 0;
        c.gridy = fila;
        form.add(new JLabel(etiqueta), c);
        c.gridx = 1;
        form.add(campo, c);
    }

    private void cargarComputadores() {
        cmbComputador.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> lista,
                    Object valor, int indice, boolean seleccionado, boolean foco) {
                String texto = (valor instanceof Computador pc)
                        ? "PC" + pc.getId() + " - " + pc.getNombre() : "";
                return super.getListCellRendererComponent(lista, texto, indice, seleccionado, foco);
            }
        });
        Nodo<Computador> actual = cluster.getComputadores().getCabeza();
        while (actual != null) {
            cmbComputador.addItem(actual.getDato());
            actual = actual.getSiguiente();
        }
    }

    private void crear() {
        try {
            String nombre = txtNombre.getText().trim();
            if (nombre.isEmpty()) {
                throw new IllegalArgumentException("El nombre no puede estar vacio.");
            }
            if (nombre.contains(",")) {
                throw new IllegalArgumentException("El nombre no puede contener comas.");
            }
            Computador destino = (Computador) cmbComputador.getSelectedItem();
            if (destino == null) {
                throw new IllegalArgumentException("Debe elegir un computador.");
            }
            int instrucciones = leerEntero(txtInstrucciones, "Instrucciones", 1, 100000);
            int memoria = leerEntero(txtMemoria, "Memoria", 1, destino.getRamTotal());
            int prioridad = leerEntero(txtPrioridad, "Prioridad", 1, 100);
            int deadline = leerEntero(txtDeadline, "Deadline", 1, 1000000);

            TipoProceso tipo = (TipoProceso) cmbTipo.getSelectedItem();
            Proceso proceso = new Proceso(nombre, tipo, prioridad, memoria, deadline, instrucciones);
            cluster.asignarProcesoManual(proceso, destino.getId());
            log.registrar("Se crea " + proceso.getEtiqueta() + " en " + destino.getNombre());
            ConfigProceso original = new ConfigProceso(nombre, tipo, prioridad, memoria, deadline,
                    instrucciones, cmbComputador.getSelectedIndex());
            limpiar();
            alCrear.accept(original);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Datos invalidos",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private static int leerEntero(JTextField campo, String nombre, int min, int max) {
        String texto = campo.getText().trim();
        int valor;
        try {
            valor = Integer.parseInt(texto);
        } catch (NumberFormatException ex) {
            campo.requestFocusInWindow();
            throw new IllegalArgumentException(nombre + " debe ser un numero entero.");
        }
        if (valor < min || valor > max) {
            campo.requestFocusInWindow();
            throw new IllegalArgumentException(nombre + " debe estar entre " + min + " y " + max + ".");
        }
        return valor;
    }

    private void limpiar() {
        txtNombre.setText("");
        txtInstrucciones.setText("");
        txtMemoria.setText("");
        txtDeadline.setText("");
        txtNombre.requestFocusInWindow();
    }
}

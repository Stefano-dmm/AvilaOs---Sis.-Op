package avilaos.gui;

import avilaos.config.ConfigBuffer;
import avilaos.config.Configuracion;
import avilaos.config.LectorFormulario;
import avilaos.estructuras.Nodo;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.sincronizacion.BufferSO;
import avilaos.sincronizacion.SemaforoSO;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;

/**
 * Pestana de buffers: formulario para crearlos y tabla con su estado global (elementos, capacidad,
 * valores de sus tres semaforos y los procesos bloqueados en cada uno).
 */
public class PanelBuffers extends JPanel {

    private static final String[] COLUMNAS = {
        "Buffer", "Anfitrion", "Elementos", "Capacidad", "vacios", "llenos", "mutex",
        "Procesos bloqueados", "Producidos", "Consumidos"
    };

    private final Cluster cluster;
    private final Configuracion config;
    private final Runnable alCrear;
    private final JTextField txtNombre = new JTextField(10);
    private final JSpinner spnCapacidad = new JSpinner(
            new SpinnerNumberModel(5, 1, Configuracion.MAX_CAPACIDAD_BUFFER, 1));
    private final JComboBox<String> cmbAnfitrion = new JComboBox<>();
    private final ModeloBuffers modelo = new ModeloBuffers();

    public PanelBuffers(Cluster cluster, Configuracion config, Runnable alCrear) {
        this.cluster = cluster;
        this.config = config;
        this.alCrear = alCrear;

        Nodo<Computador> actual = cluster.getComputadores().getCabeza();
        while (actual != null) {
            cmbAnfitrion.addItem("PC" + actual.getDato().getId() + " - " + actual.getDato().getNombre());
            actual = actual.getSiguiente();
        }

        JButton btnCrear = new JButton("Crear buffer");
        btnCrear.addActionListener(e -> crear());
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.setBorder(BorderFactory.createTitledBorder("Nuevo buffer (ocupa RAM en su anfitrion)"));
        form.add(new JLabel("Nombre:"));
        form.add(txtNombre);
        form.add(new JLabel("Capacidad:"));
        form.add(spnCapacidad);
        form.add(new JLabel("Anfitrion:"));
        form.add(cmbAnfitrion);
        form.add(btnCrear);

        JTable tabla = new JTable(modelo);
        tabla.setFillsViewportHeight(true);
        tabla.getTableHeader().setReorderingAllowed(false);
        tabla.getColumnModel().getColumn(7).setPreferredWidth(320);
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createTitledBorder("Estado de los buffers"));

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(form, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        refrescar();
    }

    private void crear() {
        try {
            ConfigBuffer datos = LectorFormulario.buffer(txtNombre.getText(),
                    String.valueOf(spnCapacidad.getValue()), cmbAnfitrion.getSelectedIndex());
            cluster.adquirir();
            try {
                Computador anfitrion = cluster.obtenerPorIndice(datos.indiceComputador());
                cluster.crearBuffer(datos.nombre(), datos.capacidad(), anfitrion.getId());
            } finally {
                cluster.liberar();
            }
            config.agregarBuffer(datos);
            txtNombre.setText("");
            alCrear.run();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Datos invalidos",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Debe llamarse con el cerrojo del cluster tomado y desde el hilo de Swing. */
    public void refrescar() {
        Object[][] filas = new Object[cluster.getBuffers().getTamanio()][];
        int i = 0;
        Nodo<BufferSO> actual = cluster.getBuffers().getCabeza();
        while (actual != null) {
            BufferSO b = actual.getDato();
            filas[i++] = new Object[] {
                b.getNombre(), b.getAnfitrion().getNombre(), b.getElementos(), b.getCapacidad(),
                b.getVacios().getValor(), b.getLlenos().getValor(), b.getMutex().getValor(),
                bloqueados(b), b.getProducidos(), b.getConsumidos()
            };
            actual = actual.getSiguiente();
        }
        modelo.reemplazar(filas);
    }

    private static String bloqueados(BufferSO b) {
        StringBuilder sb = new StringBuilder();
        agregar(sb, b.getVacios());
        agregar(sb, b.getLlenos());
        agregar(sb, b.getMutex());
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static void agregar(StringBuilder sb, SemaforoSO semaforo) {
        String espera = semaforo.describirEspera();
        if (!espera.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("  |  ");
            }
            sb.append(semaforo.getNombre()).append(": ").append(espera);
        }
    }

    private static class ModeloBuffers extends AbstractTableModel {

        private Object[][] filas = new Object[0][];

        void reemplazar(Object[][] nuevas) {
            filas = nuevas;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return filas.length;
        }

        @Override
        public int getColumnCount() {
            return COLUMNAS.length;
        }

        @Override
        public String getColumnName(int columna) {
            return COLUMNAS[columna];
        }

        @Override
        public Object getValueAt(int fila, int columna) {
            return filas[fila][columna];
        }
    }
}

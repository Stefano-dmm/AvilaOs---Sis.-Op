package avilaos.gui;

import avilaos.archivos.NodoArchivo;
import avilaos.archivos.Permiso;
import avilaos.archivos.SistemaArchivos;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

/**
 * Tabla de asignación de archivos: para cada archivo su nombre, los bloques asignados, el primer bloque, el dueño, el
 * proceso que lo creó y el color con que se dibuja en el mapa del disco. Se actualiza con cada operación.
 */
public class PanelTablaAsignacion extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient SistemaArchivos archivos;
    private final ModeloAsignacion modelo = new ModeloAsignacion();

    /** Arma la tabla sobre el sistema de archivos dado. */
    public PanelTablaAsignacion(SistemaArchivos archivos) {
        this.archivos = archivos;
        JTable tabla = new JTable(modelo);
        tabla.setFillsViewportHeight(true);
        tabla.setDefaultRenderer(Color.class, new PintorColor());
        tabla.getColumnModel().getColumn(0).setPreferredWidth(50);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(200);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createTitledBorder("Tabla de asignación de archivos"));
        add(scroll, BorderLayout.CENTER);
        refrescar();
    }

    /** Debe llamarse desde el hilo de Swing. */
    public void refrescar() {
        modelo.reemplazar(archivos.todosLosArchivos());
    }

    /** Pinta la celda del color del archivo. */
    private static class PintorColor extends JLabel implements TableCellRenderer {

        private static final long serialVersionUID = 1L;

        PintorColor() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada, boolean foco, int fila, int columna) {
            setBackground((Color) valor);
            return this;
        }
    }

    private static class ModeloAsignacion extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNAS = {"Color", "Nombre", "Ruta", "Bloques asignados", "Primer bloque", "Dueño", "Permiso", "Proceso creador"};
        private transient NodoArchivo[] filas = new NodoArchivo[0];

        void reemplazar(NodoArchivo[] nuevas) {
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
        public Class<?> getColumnClass(int columna) {
            return columna == 0 ? Color.class : Object.class;
        }

        @Override
        public Object getValueAt(int fila, int columna) {
            NodoArchivo n = filas[fila];
            return switch (columna) {
                case 0 -> ColoresArchivos.de(n.getId());
                case 1 -> n.getNombre();
                case 2 -> n.getRuta();
                case 3 -> n.getBloques();
                case 4 -> n.getPrimerBloque();
                case 5 -> n.getDueno();
                case 6 -> n.getPermiso() == Permiso.PUBLICO ? "público" : "privado";
                default -> n.getCreador();
            };
        }
    }
}

package avilaos.gui;

import avilaos.estructuras.Cola;
import avilaos.estructuras.Nodo;
import avilaos.modelo.Proceso;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

/** Tabla de solo lectura con los elementos del PCB de una cola (o de un solo proceso). */
public class TablaProcesos extends JTable {

    private static final String[] COLUMNAS = {
        "ID", "Nombre", "PC#", "Estado", "Tipo", "Prioridad", "Memoria",
        "Deadline", "Restante", "PC", "Motivo de bloqueo"
    };

    private final ModeloProcesos modelo = new ModeloProcesos();

    public TablaProcesos() {
        setModel(modelo);
        setFillsViewportHeight(true);
        getTableHeader().setReorderingAllowed(false);
        getColumnModel().getColumn(1).setPreferredWidth(90);
        getColumnModel().getColumn(3).setPreferredWidth(85);
        getColumnModel().getColumn(10).setPreferredWidth(180);
    }

    public void mostrarCola(Cola<Proceso> cola) {
        Proceso[] filas = new Proceso[cola.getTamanio()];
        Nodo<Proceso> actual = cola.getLista().getCabeza();
        int i = 0;
        while (actual != null) {
            filas[i++] = actual.getDato();
            actual = actual.getSiguiente();
        }
        modelo.reemplazar(filas);
    }

    public void mostrarProceso(Proceso proceso) {
        modelo.reemplazar(proceso == null ? new Proceso[0] : new Proceso[] {proceso});
    }

    private static class ModeloProcesos extends AbstractTableModel {

        private Proceso[] filas = new Proceso[0];

        void reemplazar(Proceso[] nuevas) {
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
            Proceso p = filas[fila];
            return switch (columna) {
                case 0 -> p.getId();
                case 1 -> p.getNombre();
                case 2 -> p.getComputadorId();
                case 3 -> p.getEstado();
                case 4 -> p.getTipo();
                case 5 -> p.getPrioridad();
                case 6 -> p.getMemoriaRequerida();
                case 7 -> p.getDeadline();
                case 8 -> p.getTiempoRestante();
                case 9 -> p.getPc();
                default -> p.getMotivoBloqueo() == null ? "" : p.getMotivoBloqueo();
            };
        }
    }
}

package avilaos.gui;

import avilaos.estructuras.Nodo;
import avilaos.metricas.CalculadorMetricas;
import avilaos.metricas.HistorialUtilizacion;
import avilaos.metricas.ResumenMetricas;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

/** Pestana de metricas: tabla por computador y del sistema, mas el grafico de utilizacion. */
public class PanelMetricas extends JPanel {

    private static final String[] COLUMNAS = {
        "Computador", "Throughput (proc/ciclo)", "Utilizacion CPU", "Respuesta prom. (ciclos)",
        "Cumplimiento deadlines", "Equidad (Jain)", "Completados", "Terminados"
    };

    private final Cluster cluster;
    private final ModeloMetricas modelo = new ModeloMetricas();
    private final GraficoUtilizacion grafico;

    public PanelMetricas(Cluster cluster, HistorialUtilizacion historial) {
        this.cluster = cluster;
        this.grafico = new GraficoUtilizacion(historial);

        JTable tabla = new JTable(modelo);
        tabla.setFillsViewportHeight(true);
        tabla.getTableHeader().setReorderingAllowed(false);
        JScrollPane scrollTabla = new JScrollPane(tabla);
        scrollTabla.setBorder(BorderFactory.createTitledBorder("Metricas de rendimiento"));
        scrollTabla.setPreferredSize(new Dimension(100, 40 + 22 * (cluster.getCantidadComputadores() + 1)));

        JPanel contenedorGrafico = new JPanel(new BorderLayout());
        contenedorGrafico.setBorder(BorderFactory.createTitledBorder("Utilizacion de CPU vs tiempo"));
        contenedorGrafico.add(grafico, BorderLayout.CENTER);

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(scrollTabla, BorderLayout.NORTH);
        add(contenedorGrafico, BorderLayout.CENTER);
        refrescar();
    }

    /** Debe llamarse desde el hilo de Swing despues de cada ciclo. */
    public void refrescar() {
        int filas = cluster.getCantidadComputadores() + 1;
        ResumenMetricas[] resumenes = new ResumenMetricas[filas];
        int i = 0;
        Nodo<Computador> actual = cluster.getComputadores().getCabeza();
        while (actual != null) {
            resumenes[i++] = CalculadorMetricas.deComputador(actual.getDato());
            actual = actual.getSiguiente();
        }
        resumenes[i] = CalculadorMetricas.global(cluster);
        modelo.reemplazar(resumenes);
        grafico.repaint();
    }

    private static String decimal(double valor) {
        return Double.isNaN(valor) ? "-" : String.format("%.2f", valor);
    }

    private static String porcentaje(double valor) {
        return Double.isNaN(valor) ? "-" : String.format("%.1f%%", valor * 100);
    }

    private static class ModeloMetricas extends AbstractTableModel {

        private ResumenMetricas[] filas = new ResumenMetricas[0];

        void reemplazar(ResumenMetricas[] nuevas) {
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
            ResumenMetricas r = filas[fila];
            return switch (columna) {
                case 0 -> r.nombre();
                case 1 -> decimal(r.throughput());
                case 2 -> porcentaje(r.utilizacion());
                case 3 -> decimal(r.respuestaPromedio());
                case 4 -> porcentaje(r.cumplimiento());
                case 5 -> decimal(r.equidad());
                case 6 -> r.completados();
                default -> r.terminados();
            };
        }
    }
}

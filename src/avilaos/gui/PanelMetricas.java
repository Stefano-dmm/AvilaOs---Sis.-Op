package avilaos.gui;

import avilaos.estructuras.Nodo;
import avilaos.metricas.CalculadorMetricas;
import avilaos.metricas.HistorialMetricas;
import avilaos.metricas.Metrica;
import avilaos.metricas.ResumenBuffer;
import avilaos.metricas.ResumenMetricas;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

/** Pestana de metricas: tablas por computador y del sistema, y un grafico con selector de metrica. */
public class PanelMetricas extends JPanel {

    private static final String[] COLUMNAS = {
        "Computador", "Throughput", "Utilizacion CPU", "Respuesta prom.", "Espera prom.",
        "Tasa de exito", "Equidad (Jain)", "Bloqueo semaforos", "Completados", "Terminados"
    };
    private static final String[] COLUMNAS_BUFFERS = {
        "Buffer", "Anfitrion", "Elementos producidos", "Elementos consumidos", "En el buffer", "Capacidad"
    };

    private final Cluster cluster;
    private final ModeloMetricas modelo = new ModeloMetricas();
    private final ModeloBuffers modeloBuffers = new ModeloBuffers();
    private final GraficoMetricas grafico;
    private final JComboBox<Metrica> selectorMetrica = new JComboBox<>(Metrica.values());

    /** Arma las dos tablas y el grafico, con un selector para elegir la metrica que se dibuja. */
    public PanelMetricas(Cluster cluster, HistorialMetricas historial) {
        this.cluster = cluster;
        this.grafico = new GraficoMetricas(historial);
        selectorMetrica.addActionListener(e -> grafico.setMetrica((Metrica) selectorMetrica.getSelectedItem()));

        JScrollPane scrollTabla = envolver("Metricas de rendimiento (throughput en procesos por ciclo; respuesta, espera "
                + "y bloqueo en ciclos; tasa de exito = deadlines cumplidos)", new JTable(modelo));
        scrollTabla.setPreferredSize(new Dimension(100, 40 + 22 * (cluster.getCantidadComputadores() + 1)));
        JScrollPane scrollBuffers = envolver("Productor-consumidor por buffer", new JTable(modeloBuffers));
        scrollBuffers.setPreferredSize(new Dimension(100, 90));

        JPanel tablas = new JPanel(new BorderLayout(8, 8));
        tablas.add(scrollTabla, BorderLayout.NORTH);
        tablas.add(scrollBuffers, BorderLayout.CENTER);

        JPanel contenedorGrafico = new JPanel(new BorderLayout());
        contenedorGrafico.setBorder(BorderFactory.createTitledBorder("Grafico de metricas vs tiempo"));
        JPanel filaSelector = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filaSelector.add(new JLabel("Metrica:"));
        filaSelector.add(selectorMetrica);
        contenedorGrafico.add(filaSelector, BorderLayout.NORTH);
        contenedorGrafico.add(grafico, BorderLayout.CENTER);

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(tablas, BorderLayout.NORTH);
        add(contenedorGrafico, BorderLayout.CENTER);
        refrescar();
    }

    /** Pone una tabla dentro de un panel con titulo. */
    private static JScrollPane envolver(String titulo, JTable tabla) {
        tabla.setFillsViewportHeight(true);
        tabla.getTableHeader().setReorderingAllowed(false);
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createTitledBorder(titulo));
        return scroll;
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
        modeloBuffers.reemplazar(CalculadorMetricas.deBuffers(cluster));
        grafico.repaint();
    }

    /** Formato de un decimal; un valor que no se puede calcular todavia se muestra como un guion. */
    private static String decimal(double valor) {
        return Double.isNaN(valor) ? "-" : String.format("%.2f", valor);
    }

    /** Formato de una fraccion como porcentaje; un valor que no se puede calcular todavia se muestra como un guion. */
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
                case 4 -> decimal(r.esperaPromedio());
                case 5 -> porcentaje(r.cumplimiento());
                case 6 -> decimal(r.equidad());
                case 7 -> decimal(r.bloqueoSemaforoPromedio());
                case 8 -> r.completados();
                default -> r.terminados();
            };
        }
    }

    private static class ModeloBuffers extends AbstractTableModel {

        private ResumenBuffer[] filas = new ResumenBuffer[0];

        void reemplazar(ResumenBuffer[] nuevas) {
            filas = nuevas;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return filas.length;
        }

        @Override
        public int getColumnCount() {
            return COLUMNAS_BUFFERS.length;
        }

        @Override
        public String getColumnName(int columna) {
            return COLUMNAS_BUFFERS[columna];
        }

        @Override
        public Object getValueAt(int fila, int columna) {
            ResumenBuffer r = filas[fila];
            return switch (columna) {
                case 0 -> r.nombre();
                case 1 -> r.anfitrion();
                case 2 -> r.producidos();
                case 3 -> r.consumidos();
                case 4 -> r.elementos();
                default -> r.capacidad();
            };
        }
    }
}

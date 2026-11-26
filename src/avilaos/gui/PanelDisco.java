package avilaos.gui;

import avilaos.archivos.NodoArchivo;
import avilaos.disco.DireccionCabezal;
import avilaos.disco.Disco;
import avilaos.disco.PoliticaDisco;
import avilaos.disco.SolicitudES;
import avilaos.nucleo.SistemaAvilaOS;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;

/**
 * Vista del disco: el mapa de bloques con el cabezal, la cola de E/S con la operación de cada solicitud, el selector de
 * política (que se puede cambiar con el sistema corriendo) y las cifras del disco. Solo observa; la lógica vive en Disco.
 */
public class PanelDisco extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient SistemaAvilaOS sistema;
    private final transient LogEventos log;
    private final PanelMapaDisco mapa = new PanelMapaDisco();
    private final JLabel lblCabezal = new JLabel();
    private final JLabel lblServicio = new JLabel();
    private final JLabel lblLibres = new JLabel();
    private final JComboBox<PoliticaDisco> cmbPolitica = new JComboBox<>(PoliticaDisco.values());
    private final JSpinner spnVelocidad;
    private final JCheckBox chkRetener = new JCheckBox("Retener el disco (acumula la cola)");
    private final ModeloCola modeloCola = new ModeloCola();
    private boolean actualizando;

    /** Arma la vista del disco del sistema y conecta el selector de política, la velocidad y la retención. */
    public PanelDisco(SistemaAvilaOS sistema, LogEventos log) {
        this.sistema = sistema;
        this.log = log;
        this.spnVelocidad = new JSpinner(new SpinnerNumberModel(sistema.getDisco().getVelocidad(), 1, 100, 1));
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controles.add(new JLabel("Política del disco:"));
        controles.add(cmbPolitica);
        controles.add(new JLabel("Cabezal (posiciones por ciclo):"));
        controles.add(spnVelocidad);
        controles.add(chkRetener);
        JPanel cifras = new JPanel(new GridLayout(3, 1));
        cifras.add(lblCabezal);
        cifras.add(lblServicio);
        cifras.add(lblLibres);
        JPanel norte = new JPanel(new BorderLayout());
        norte.add(controles, BorderLayout.NORTH);
        norte.add(cifras, BorderLayout.CENTER);
        add(norte, BorderLayout.NORTH);

        JScrollPane scrollMapa = new JScrollPane(mapa);
        scrollMapa.setBorder(BorderFactory.createTitledBorder("Mapa de bloques del disco"));
        JTable tabla = new JTable(modeloCola);
        tabla.setFillsViewportHeight(true);
        JScrollPane scrollCola = new JScrollPane(tabla);
        scrollCola.setBorder(BorderFactory.createTitledBorder("Cola de E/S del disco"));
        scrollCola.setPreferredSize(new Dimension(100, 170));
        add(scrollMapa, BorderLayout.CENTER);
        add(scrollCola, BorderLayout.SOUTH);
        conectarControles();
        refrescar();
    }

    /** Los cambios esperan a que termine el ciclo en curso del reloj (el cerrojo del cluster). */
    private void conectarControles() {
        cmbPolitica.addActionListener(e -> {
            PoliticaDisco elegida = (PoliticaDisco) cmbPolitica.getSelectedItem();
            if (actualizando || elegida == null || elegida == sistema.getDisco().getNombrePolitica()) {
                return;
            }
            sistema.getCluster().adquirir();
            try {
                sistema.getDisco().setPolitica(elegida);
            } finally {
                sistema.getCluster().liberar();
            }
            log.registrar("El disco cambia su política de planificación a " + elegida);
        });
        spnVelocidad.addChangeListener(e -> {
            if (actualizando) {
                return;
            }
            sistema.getDisco().setVelocidad((Integer) spnVelocidad.getValue());
            log.registrar("El cabezal ahora recorre " + spnVelocidad.getValue() + " posiciones por ciclo");
        });
        chkRetener.addActionListener(e -> {
            sistema.getDisco().setRetenido(chkRetener.isSelected());
            log.registrar(chkRetener.isSelected()
                    ? "El disco queda retenido: las solicitudes nuevas se acumulan en la cola"
                    : "El disco se suelta: atiende la cola con la política " + sistema.getDisco().getNombrePolitica());
        });
    }

    /** En modo usuario estos controles se bloquean: solo el administrador gestiona el disco. */
    public void setControlesHabilitados(boolean habilitados) {
        cmbPolitica.setEnabled(habilitados);
        spnVelocidad.setEnabled(habilitados);
        chkRetener.setEnabled(habilitados);
    }

    /** Debe llamarse desde el hilo de Swing con el cerrojo del cluster tomado. */
    public void refrescar() {
        actualizando = true;
        try {
            Disco disco = sistema.getDisco();
            lblCabezal.setText("Cabezal en el bloque " + disco.getPosicion() + "   |   dirección: "
                    + (disco.getDireccion() == DireccionCabezal.SUBE ? "hacia los bloques mayores (↑)" : "hacia los bloques menores (↓)")
                    + "   |   desplazamiento total: " + disco.getDesplazamientoTotal() + " bloques");
            SolicitudES enServicio = disco.getEnServicio();
            lblServicio.setText("Atendiendo: " + (enServicio == null ? "nada (disco libre)" : enServicio + " [" + enServicio.getOperacion() + "]")
                    + "   |   en cola: " + disco.getTamanioCola() + "   |   atendidas: " + disco.getAtendidas());
            lblLibres.setText("Bloques libres: " + disco.getBloquesLibresArchivos() + " de " + disco.getBloquesArchivos()
                    + " en archivos   |   " + disco.getBloquesLibresSwap() + " de " + disco.getBloquesSwap() + " en el swap");
            cmbPolitica.setSelectedItem(disco.getNombrePolitica());
            chkRetener.setSelected(disco.isRetenido());
            mapa.refrescar(disco, this::describirDueno);
            modeloCola.reemplazar(enServicio, disco.copiarCola());
        } finally {
            actualizando = false;
        }
    }

    /** A quién pertenece un bloque, para el texto emergente del mapa. */
    private String describirDueno(int dueno) {
        if (dueno < 0) {
            return "memoria del proceso suspendido P" + (-dueno) + " (swap)";
        }
        NodoArchivo archivo = sistema.getArchivos().buscarPorId(dueno);
        return archivo == null ? "archivo en creación" : "archivo " + archivo.getRuta();
    }

    /** Tabla de la cola de E/S: la solicitud en servicio primero y después las que esperan. */
    private static class ModeloCola extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNAS = {"Estado", "Solicitud", "Proceso", "Operación", "Bloque", "Bloques a transferir", "Ruta"};
        private transient SolicitudES[] filas = new SolicitudES[0];
        private boolean hayEnServicio;

        void reemplazar(SolicitudES enServicio, SolicitudES[] enCola) {
            hayEnServicio = enServicio != null;
            filas = new SolicitudES[enCola.length + (hayEnServicio ? 1 : 0)];
            int i = 0;
            if (hayEnServicio) {
                filas[i++] = enServicio;
            }
            System.arraycopy(enCola, 0, filas, i, enCola.length);
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
            SolicitudES s = filas[fila];
            return switch (columna) {
                case 0 -> (hayEnServicio && fila == 0) ? "En servicio" : "En espera";
                case 1 -> "#" + s.getId();
                case 2 -> s.getEtiquetaProceso();
                case 3 -> s.getOperacion();
                case 4 -> s.getBloque();
                case 5 -> s.getBloquesATransferir();
                default -> s.getRuta() == null ? "(swap)" : s.getRuta();
            };
        }
    }
}

package avilaos.gui;

import avilaos.estructuras.Cola;
import avilaos.estructuras.Nodo;
import avilaos.modelo.ModoCPU;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.nucleo.Computador;
import avilaos.nucleo.politicas.FabricaPoliticas;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;

/** Vista 2: muestra el estado de un computador. Solo observa; la logica vive en Computador. */
public class PanelComputador extends JPanel {

    private final Computador computador;
    private final JLabel lblCpu = new JLabel();
    private final JLabel lblModo = new JLabel();
    private final JProgressBar barraRam = new JProgressBar();
    private final JComboBox<PoliticaPlanificacion> cmbPolitica =
            new JComboBox<>(PoliticaPlanificacion.values());
    private final JSpinner spnQuantum;
    private final JTextArea txtNuevos = crearArea();
    private final JTextArea txtListos = crearArea();
    private final JTextArea txtBloqueados = crearArea();
    private final JTextArea txtTerminados = crearArea();
    private final LogEventos log;
    private boolean actualizando;

    public PanelComputador(Computador computador, LogEventos log) {
        this.computador = computador;
        this.log = log;
        this.spnQuantum = new JSpinner(new SpinnerNumberModel(computador.getQuantum(), 1, 1000, 1));
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(crearCabecera(), BorderLayout.NORTH);
        add(crearColas(), BorderLayout.CENTER);
        conectarControles();
        refrescar();
    }

    private JPanel crearCabecera() {
        JPanel cabecera = new JPanel(new GridLayout(4, 1, 4, 4));
        cabecera.setBorder(BorderFactory.createTitledBorder(
                computador.getNombre() + " (PC" + computador.getId() + ")"));

        JPanel filaPolitica = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        filaPolitica.add(new JLabel("Politica:"));
        filaPolitica.add(cmbPolitica);
        filaPolitica.add(new JLabel("Quantum:"));
        filaPolitica.add(spnQuantum);

        barraRam.setStringPainted(true);
        cabecera.add(filaPolitica);
        cabecera.add(lblCpu);
        cabecera.add(lblModo);
        cabecera.add(barraRam);
        return cabecera;
    }

    private JPanel crearColas() {
        JPanel colas = new JPanel(new GridLayout(2, 2, 8, 8));
        colas.add(envolver("Nuevos", txtNuevos));
        colas.add(envolver("Listos", txtListos));
        colas.add(envolver("Bloqueados", txtBloqueados));
        colas.add(envolver("Terminados", txtTerminados));
        return colas;
    }

    private void conectarControles() {
        cmbPolitica.addActionListener(e -> {
            if (actualizando) {
                return;
            }
            PoliticaPlanificacion elegida = (PoliticaPlanificacion) cmbPolitica.getSelectedItem();
            if (elegida != null && elegida != computador.getNombrePolitica()) {
                computador.setPolitica(FabricaPoliticas.crear(elegida), elegida);
                log.registrar(computador.getNombre() + " cambia su politica a " + elegida);
            }
        });
        spnQuantum.addChangeListener(e -> {
            if (actualizando) {
                return;
            }
            int q = (Integer) spnQuantum.getValue();
            computador.setQuantum(q);
            log.registrar(computador.getNombre() + " cambia su quantum a " + q);
        });
    }

    /** Debe llamarse desde el hilo de Swing. */
    public void refrescar() {
        actualizando = true;
        try {
            Proceso enCpu = computador.getProcesoEnCPU();
            if (enCpu == null) {
                lblCpu.setText("CPU: libre");
            } else {
                lblCpu.setText("CPU: " + enCpu.getEtiqueta()
                        + " | PC=" + enCpu.getPc()
                        + " | prioridad=" + enCpu.getPrioridad()
                        + " | deadline=" + enCpu.getDeadline()
                        + " | restante=" + enCpu.getTiempoRestante());
            }
            lblModo.setText("Modo: " + (computador.getModoCPU() == ModoCPU.USUARIO
                    ? "programa de usuario" : "sistema operativo"));

            barraRam.setMaximum(computador.getRamTotal());
            barraRam.setValue(computador.getRamUsada());
            barraRam.setString("RAM " + computador.getRamUsada() + " usada / "
                    + computador.getRamLibre() + " libre (total " + computador.getRamTotal() + ")");

            if (computador.getNombrePolitica() != null) {
                cmbPolitica.setSelectedItem(computador.getNombrePolitica());
            }
            spnQuantum.setValue(computador.getQuantum());

            txtNuevos.setText(describir(computador.getColaNuevos()));
            txtListos.setText(describir(computador.getColaListos()));
            txtBloqueados.setText(describir(computador.getColaBloqueados()));
            txtTerminados.setText(describir(computador.getColaTerminados()));
        } finally {
            actualizando = false;
        }
    }

    private static String describir(Cola<Proceso> cola) {
        StringBuilder sb = new StringBuilder();
        Nodo<Proceso> actual = cola.getLista().getCabeza();
        while (actual != null) {
            Proceso p = actual.getDato();
            sb.append(p.getEtiqueta())
                    .append(" [").append(p.getTipo())
                    .append(", pri=").append(p.getPrioridad())
                    .append(", mem=").append(p.getMemoriaRequerida())
                    .append(", dl=").append(p.getDeadline())
                    .append(", rest=").append(p.getTiempoRestante())
                    .append("]\n");
            actual = actual.getSiguiente();
        }
        return sb.toString();
    }

    private static JTextArea crearArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        return area;
    }

    private static JScrollPane envolver(String titulo, JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createTitledBorder(titulo));
        return scroll;
    }
}

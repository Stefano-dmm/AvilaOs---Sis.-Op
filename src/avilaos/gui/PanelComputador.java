package avilaos.gui;

import avilaos.modelo.ModoCPU;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.nucleo.politicas.FabricaPoliticas;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/** Vista 2: muestra el estado de un computador. Solo observa; la logica vive en Computador. */
public class PanelComputador extends JPanel {

    private final Computador computador;
    private final JLabel lblModo = new JLabel();
    private final JProgressBar barraRam = new JProgressBar();
    private final JComboBox<PoliticaPlanificacion> cmbPolitica =
            new JComboBox<>(PoliticaPlanificacion.values());
    private final JSpinner spnQuantum;
    private final TablaProcesos tablaCpu = new TablaProcesos();
    private final TablaProcesos tablaNuevos = new TablaProcesos();
    private final TablaProcesos tablaListos = new TablaProcesos();
    private final TablaProcesos tablaBloqueados = new TablaProcesos();
    private final TablaProcesos tablaTerminados = new TablaProcesos();
    private final LogEventos log;
    private boolean actualizando;

    public PanelComputador(Computador computador, LogEventos log) {
        this.computador = computador;
        this.log = log;
        this.spnQuantum = new JSpinner(new SpinnerNumberModel(computador.getQuantum(), 1, 1000, 1));
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(crearCabecera(), BorderLayout.NORTH);
        add(crearCuerpo(), BorderLayout.CENTER);
        conectarControles();
        refrescar();
    }

    private JPanel crearCabecera() {
        JPanel cabecera = new JPanel(new GridLayout(3, 1, 4, 4));
        cabecera.setBorder(BorderFactory.createTitledBorder(
                computador.getNombre() + " (PC" + computador.getId() + ")"));

        JPanel filaPolitica = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filaPolitica.add(new JLabel("Politica:"));
        filaPolitica.add(cmbPolitica);
        filaPolitica.add(new JLabel("Quantum:"));
        filaPolitica.add(spnQuantum);

        barraRam.setStringPainted(true);
        cabecera.add(filaPolitica);
        cabecera.add(lblModo);
        cabecera.add(barraRam);
        return cabecera;
    }

    private JPanel crearCuerpo() {
        JScrollPane scrollCpu = envolver("Proceso en CPU", tablaCpu);
        scrollCpu.setPreferredSize(new Dimension(100, 85));

        JPanel colas = new JPanel(new GridLayout(2, 2, 8, 8));
        colas.add(envolver("Nuevos", tablaNuevos));
        colas.add(envolver("Listos", tablaListos));
        colas.add(envolver("Bloqueados", tablaBloqueados));
        colas.add(envolver("Terminados", tablaTerminados));

        JPanel cuerpo = new JPanel(new BorderLayout(8, 8));
        cuerpo.add(scrollCpu, BorderLayout.NORTH);
        cuerpo.add(colas, BorderLayout.CENTER);
        return cuerpo;
    }

    private void conectarControles() {
        cmbPolitica.addActionListener(e -> {
            if (actualizando) {
                return;
            }
            PoliticaPlanificacion elegida = (PoliticaPlanificacion) cmbPolitica.getSelectedItem();
            if (elegida != null && elegida != computador.getNombrePolitica()) {
                conCerrojo(() -> computador.setPolitica(FabricaPoliticas.crear(elegida), elegida));
                log.registrar(computador.getNombre() + " cambia su politica a " + elegida);
            }
        });
        spnQuantum.addChangeListener(e -> {
            if (actualizando) {
                return;
            }
            int q = (Integer) spnQuantum.getValue();
            conCerrojo(() -> computador.setQuantum(q));
            log.registrar(computador.getNombre() + " cambia su quantum a " + q);
        });
    }

    /** Los cambios hechos desde la ventana esperan a que termine el ciclo en curso del reloj. */
    private void conCerrojo(Runnable cambio) {
        Cluster cluster = computador.getCluster();
        if (cluster != null) {
            cluster.adquirir();
        }
        try {
            cambio.run();
        } finally {
            if (cluster != null) {
                cluster.liberar();
            }
        }
    }

    /** Debe llamarse desde el hilo de Swing. */
    public void refrescar() {
        actualizando = true;
        try {
            lblModo.setText("Modo: " + (computador.getModoCPU() == ModoCPU.USUARIO
                    ? "ejecutando programa de usuario" : "ejecutando sistema operativo")
                    + "   |   PC=" + computador.getCpu().getPc() + "   MAR=" + computador.getCpu().getMar());

            barraRam.setMaximum(computador.getRamTotal());
            barraRam.setValue(computador.getRamUsada());
            barraRam.setString("RAM " + computador.getRamUsada() + " usada / "
                    + computador.getRamLibre() + " libre (total " + computador.getRamTotal() + ")");

            if (computador.getNombrePolitica() != null) {
                cmbPolitica.setSelectedItem(computador.getNombrePolitica());
            }
            spnQuantum.setValue(computador.getQuantum());

            tablaCpu.mostrarProceso(computador.getProcesoEnCPU());
            tablaNuevos.mostrarCola(computador.getColaNuevos());
            tablaListos.mostrarCola(computador.getColaListos());
            tablaBloqueados.mostrarCola(computador.getColaBloqueados());
            tablaTerminados.mostrarCola(computador.getColaTerminados());
        } finally {
            actualizando = false;
        }
    }

    private static JScrollPane envolver(String titulo, TablaProcesos tabla) {
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createTitledBorder(titulo));
        return scroll;
    }
}

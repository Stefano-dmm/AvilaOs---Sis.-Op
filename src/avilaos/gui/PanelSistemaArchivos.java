package avilaos.gui;

import avilaos.archivos.ControlAcceso;
import avilaos.archivos.NodoArchivo;
import avilaos.archivos.Permiso;
import avilaos.archivos.Sesion;
import avilaos.archivos.SistemaArchivos;
import avilaos.archivos.TipoNodo;
import avilaos.config.LectorFormulario;
import avilaos.disco.OperacionES;
import avilaos.modelo.Proceso;
import avilaos.nucleo.SistemaAvilaOS;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * Sistema de archivos en un JTree, con la información del elemento seleccionado y el formulario para crear procesos de E/S.
 * La interfaz nunca opera sobre los archivos: solo crea el proceso, que pasa por las colas del computador y por el disco.
 * En modo usuario explica por qué se rechaza una operación sin interrumpir la simulación.
 */
public class PanelSistemaArchivos extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Lo que cuelga de cada nodo del JTree: el elemento y el texto con que se muestra. */
    private record Elemento(NodoArchivo nodo) {
        @Override
        public String toString() {
            return nodo.esDirectorio() ? nodo.toString() : nodo + "  (" + nodo.getBloques() + " bloques)";
        }
    }

    private final transient SistemaAvilaOS sistema;
    private final transient Supplier<Sesion> sesion;
    private final transient LogEventos log;
    private final transient Runnable alCrear;
    private final DefaultTreeModel modelo = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final JTree arbol = new JTree(modelo);
    private final JTextArea info = new JTextArea(8, 28);
    /** Texto que resume el árbol la última vez que se dibujó; si no cambió no se reconstruye (así no se pierde lo expandido ni lo seleccionado). */
    private String firmaDelArbol = "";

    private final JTextField txtNombre = new JTextField("proc_es", 10);
    private final JTextField txtPrioridad = new JTextField("5", 4);
    private final JTextField txtMemoria = new JTextField("16", 4);
    private final JTextField txtDeadline = new JTextField("500", 5);
    private final JTextField txtInstrucciones = new JTextField("2", 4);
    private final JComboBox<OperacionES> cmbOperacion = new JComboBox<>(new OperacionES[] {
        OperacionES.CREAR, OperacionES.LEER, OperacionES.ACTUALIZAR, OperacionES.ELIMINAR});
    private final JTextField txtRuta = new JTextField("/", 22);
    private final JComboBox<TipoNodo> cmbTipo = new JComboBox<>(TipoNodo.values());
    private final JTextField txtBloques = new JTextField("4", 4);
    private final JComboBox<Permiso> cmbPermiso = new JComboBox<>(Permiso.values());
    private final JTextField txtNuevoNombre = new JTextField(10);

    /** Arma el panel; la sesión dice quién está usando el sistema y alCrear se llama al crear un proceso (para refrescar las vistas). */
    public PanelSistemaArchivos(SistemaAvilaOS sistema, Supplier<Sesion> sesion, LogEventos log, Runnable alCrear) {
        this.sistema = sistema;
        this.sesion = sesion;
        this.log = log;
        this.alCrear = alCrear;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        arbol.setRootVisible(true);
        arbol.addTreeSelectionListener(e -> alSeleccionar());
        JScrollPane scrollArbol = new JScrollPane(arbol);
        scrollArbol.setBorder(BorderFactory.createTitledBorder("Directorios y archivos"));
        scrollArbol.setPreferredSize(new Dimension(300, 300));

        info.setEditable(false);
        JScrollPane scrollInfo = new JScrollPane(info);
        scrollInfo.setBorder(BorderFactory.createTitledBorder("Elemento seleccionado"));

        JPanel derecha = new JPanel(new BorderLayout(8, 8));
        derecha.add(scrollInfo, BorderLayout.NORTH);
        derecha.add(crearFormulario(), BorderLayout.CENTER);
        JSplitPane division = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollArbol, derecha);
        division.setResizeWeight(0.35);
        add(division, BorderLayout.CENTER);
        refrescar();
    }

    private JPanel crearFormulario() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Crear un proceso de E/S (la operación la hace el proceso, no la interfaz)"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        agregar(form, c, 0, "Operación:", cmbOperacion);
        agregar(form, c, 1, "Ruta (archivo o directorio):", txtRuta);
        agregar(form, c, 2, "Nombre del proceso:", txtNombre);
        agregar(form, c, 3, "Prioridad (1-100):", txtPrioridad);
        agregar(form, c, 4, "Memoria (unidades):", txtMemoria);
        agregar(form, c, 5, "Deadline (ciclos):", txtDeadline);
        agregar(form, c, 6, "Instrucciones:", txtInstrucciones);
        agregar(form, c, 7, "Al crear: tipo:", cmbTipo);
        agregar(form, c, 8, "Al crear un archivo: bloques:", txtBloques);
        agregar(form, c, 9, "Al crear: permiso:", cmbPermiso);
        agregar(form, c, 10, "Al actualizar: nombre nuevo (vacío = reescribir):", txtNuevoNombre);
        JButton boton = new JButton("Crear proceso de E/S");
        boton.addActionListener(e -> crearProceso());
        c.gridx = 0;
        c.gridy = 11;
        c.gridwidth = 2;
        form.add(boton, c);
        JPanel envoltorio = new JPanel(new BorderLayout());
        envoltorio.add(form, BorderLayout.NORTH);
        return envoltorio;
    }

    private static void agregar(JPanel panel, GridBagConstraints c, int fila, String etiqueta, java.awt.Component campo) {
        c.gridy = fila;
        c.gridwidth = 1;
        c.gridx = 0;
        panel.add(new JLabel(etiqueta), c);
        c.gridx = 1;
        panel.add(campo, c);
    }

    /** Muestra la información del elemento seleccionado y pone su ruta en el formulario. */
    private void alSeleccionar() {
        TreePath camino = arbol.getSelectionPath();
        if (camino == null || !(((DefaultMutableTreeNode) camino.getLastPathComponent()).getUserObject() instanceof Elemento e)) {
            info.setText("");
            return;
        }
        NodoArchivo n = e.nodo();
        info.setText("Nombre: " + (n.getRuta().equals("/") ? "/ (raíz)" : n.getNombre())
                + "\nRuta: " + n.getRuta()
                + "\nTipo: " + (n.esDirectorio() ? "directorio" : "archivo")
                + "\nTamaño en bloques: " + (n.esDirectorio() ? "— (no ocupa bloques)" : n.getBloques())
                + "\nDueño: " + n.getDueno()
                + "\nPermisos: " + (n.getPermiso() == Permiso.PUBLICO ? "público" : "privado")
                + "\nPrimer bloque: " + (n.esDirectorio() ? "—" : n.getPrimerBloque())
                + "\nCreado por: " + n.getCreador());
        String ruta = n.getRuta();
        txtRuta.setText(cmbOperacion.getSelectedItem() == OperacionES.CREAR && n.esDirectorio()
                ? (ruta.equals("/") ? "/" : ruta + "/") : ruta);
    }

    /** Valida el formulario y, si todo es correcto y está permitido, crea el proceso y lo entrega al computador. */
    private void crearProceso() {
        try {
            int ram = sistema.getComputador().getRamTotal();
            String nombre = LectorFormulario.nombre(txtNombre.getText(), "El nombre del proceso");
            int prioridad = LectorFormulario.entero(txtPrioridad.getText(), "La prioridad", 1, SistemaAvilaOS.MAX_PRIORIDAD);
            int memoria = LectorFormulario.entero(txtMemoria.getText(), "La memoria", 1, ram);
            int deadline = LectorFormulario.entero(txtDeadline.getText(), "El deadline", 1, SistemaAvilaOS.MAX_DEADLINE);
            int instrucciones = LectorFormulario.entero(txtInstrucciones.getText(), "Las instrucciones", 1, 100000);
            OperacionES operacion = (OperacionES) cmbOperacion.getSelectedItem();
            String ruta = SistemaArchivos.normalizar(txtRuta.getText());
            TipoNodo tipo = (TipoNodo) cmbTipo.getSelectedItem();
            int bloques = 1;
            if (operacion == OperacionES.CREAR && tipo == TipoNodo.ARCHIVO) {
                bloques = LectorFormulario.entero(txtBloques.getText(), "Los bloques del archivo", 1, sistema.getDisco().getBloquesArchivos());
            }
            String nuevoNombre = txtNuevoNombre.getText().trim();
            if (operacion == OperacionES.ACTUALIZAR && !nuevoNombre.isEmpty()) {
                SistemaArchivos.validarNombre(nuevoNombre);
            }
            Sesion quien = sesion.get();
            String rechazo = motivoDeRechazo(quien, operacion, ruta, operacion == OperacionES.ACTUALIZAR && !nuevoNombre.isEmpty());
            if (rechazo != null) {
                log.registrar("Operación rechazada para " + quien.usuario() + ": " + operacion + " " + ruta + " — " + rechazo);
                JOptionPane.showMessageDialog(this, rechazo, "Operación no permitida", JOptionPane.WARNING_MESSAGE);
                return;
            }
            sistema.getCluster().adquirir();
            try {
                Proceso proceso = sistema.crearProcesoArchivo(nombre, prioridad, memoria, deadline, instrucciones, operacion, ruta,
                        quien.usuario(), tipo, bloques, (Permiso) cmbPermiso.getSelectedItem(), nuevoNombre.isEmpty() ? null : nuevoNombre);
                sistema.iniciar(proceso);
                log.registrar("Se crea el proceso " + proceso.getEtiqueta() + ": " + operacion + " " + ruta + " (usuario " + quien.usuario() + ")");
            } finally {
                sistema.getCluster().liberar();
            }
            alCrear.run();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Dato inválido", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Motivo por el que la sesión no puede hacer la operación (o null si puede); en CREAR se mira el directorio donde se crea. */
    private String motivoDeRechazo(Sesion quien, OperacionES operacion, String ruta, boolean renombra) {
        NodoArchivo objetivo;
        if (operacion == OperacionES.CREAR) {
            int corte = ruta.lastIndexOf('/');
            objetivo = sistema.getArchivos().buscar(corte <= 0 ? "/" : ruta.substring(0, corte));
        } else {
            objetivo = sistema.getArchivos().buscar(ruta);
        }
        return ControlAcceso.motivoDeRechazo(quien, operacion, objetivo, renombra);
    }

    /** Vuelve a dibujar el árbol si cambió. Debe llamarse desde el hilo de Swing. */
    public void refrescar() {
        StringBuilder firma = new StringBuilder();
        resumir(sistema.getArchivos().getRaiz(), firma);
        if (firma.toString().equals(firmaDelArbol)) {
            return;
        }
        firmaDelArbol = firma.toString();
        TreePath seleccionado = arbol.getSelectionPath();
        String rutaSeleccionada = seleccionado == null ? null
                : ((Elemento) ((DefaultMutableTreeNode) seleccionado.getLastPathComponent()).getUserObject()).nodo().getRuta();
        DefaultMutableTreeNode raiz = new DefaultMutableTreeNode(new Elemento(sistema.getArchivos().getRaiz()));
        construir(raiz, sistema.getArchivos().getRaiz());
        modelo.setRoot(raiz);
        for (int i = 0; i < arbol.getRowCount(); i++) {
            arbol.expandRow(i);
        }
        if (rutaSeleccionada != null) {
            seleccionarRuta(raiz, rutaSeleccionada);
        }
    }

    private void resumir(NodoArchivo n, StringBuilder firma) {
        firma.append(n.getRuta()).append(':').append(n.getBloques()).append(';');
        for (NodoArchivo hijo : sistema.getArchivos().hijosDe(n)) {
            resumir(hijo, firma);
        }
    }

    private void construir(DefaultMutableTreeNode padre, NodoArchivo n) {
        for (NodoArchivo hijo : sistema.getArchivos().hijosDe(n)) {
            DefaultMutableTreeNode nodo = new DefaultMutableTreeNode(new Elemento(hijo));
            padre.add(nodo);
            construir(nodo, hijo);
        }
    }

    private void seleccionarRuta(DefaultMutableTreeNode nodo, String ruta) {
        if (nodo.getUserObject() instanceof Elemento e && e.nodo().getRuta().equals(ruta)) {
            arbol.setSelectionPath(new TreePath(nodo.getPath()));
            return;
        }
        for (int i = 0; i < nodo.getChildCount(); i++) {
            seleccionarRuta((DefaultMutableTreeNode) nodo.getChildAt(i), ruta);
        }
    }
}

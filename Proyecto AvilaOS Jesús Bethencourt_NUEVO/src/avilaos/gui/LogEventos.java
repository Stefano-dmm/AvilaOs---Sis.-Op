package avilaos.gui;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/** Log de eventos de texto visible en la ventana principal. */
public class LogEventos {

    private final JTextArea area = new JTextArea();
    private final JScrollPane scroll = new JScrollPane(area);
    private int cicloActual;

    public LogEventos() {
        area.setEditable(false);
    }

    public JScrollPane getComponente() {
        return scroll;
    }

    public void setCicloActual(int ciclo) {
        this.cicloActual = ciclo;
    }

    public void registrar(String mensaje) {
        Runnable agregar = () -> {
            area.append("[ciclo " + cicloActual + "] " + mensaje + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        };
        if (SwingUtilities.isEventDispatchThread()) {
            agregar.run();
        } else {
            SwingUtilities.invokeLater(agregar);
        }
    }
}

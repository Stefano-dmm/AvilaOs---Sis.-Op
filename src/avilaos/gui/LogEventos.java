package avilaos.gui;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/** Log de eventos de texto visible en la ventana principal. Se puede escribir desde cualquier hilo. */
public class LogEventos {

    private final JTextArea area = new JTextArea();
    private final JScrollPane scroll = new JScrollPane(area);
    private volatile int cicloActual;

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
        final String linea = "[ciclo " + cicloActual + "] " + mensaje + "\n";
        Runnable agregar = () -> {
            area.append(linea);
            area.setCaretPosition(area.getDocument().getLength());
        };
        if (SwingUtilities.isEventDispatchThread()) {
            agregar.run();
        } else {
            SwingUtilities.invokeLater(agregar);
        }
    }
}

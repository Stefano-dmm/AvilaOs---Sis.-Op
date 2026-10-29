package avilaos.gui;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/** Log de eventos de texto visible en la ventana principal. Se puede escribir desde cualquier hilo. */
public class LogEventos {

    /** Area de texto donde se acumulan los mensajes; solo se modifica desde el hilo de Swing. */
    private final JTextArea area = new JTextArea();
    private final JScrollPane scroll = new JScrollPane(area);
    /** Ciclo que se antepone a cada mensaje; volatile porque lo escribe el hilo del reloj y lo leen los hilos que registran eventos. */
    private volatile int cicloActual;

    /** Crea el log con el area de texto de solo lectura. */
    public LogEventos() {
        area.setEditable(false);
    }

    public JScrollPane getComponente() {
        return scroll;
    }

    public void setCicloActual(int ciclo) {
        this.cicloActual = ciclo;
    }

    /** Agrega un mensaje con el ciclo actual. Desde otro hilo lo encola en el hilo de Swing (invokeLater), asi es seguro llamarlo desde los computadores. */
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

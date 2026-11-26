package avilaos.gui;

import avilaos.disco.DireccionCabezal;
import avilaos.disco.Disco;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.function.IntFunction;
import javax.swing.JPanel;

/**
 * Mapa visual del disco: una casilla numerada por bloque, de color según quién la ocupa (libre, un archivo o un
 * proceso suspendido en el swap), con el área de swap separada y el cabezal marcado. Solo dibuja lo que le dan.
 */
public class PanelMapaDisco extends JPanel {

    private static final long serialVersionUID = 1L;
    /** Lado de cada casilla, en píxeles. */
    private static final int LADO = 26;
    /** Espacio entre casillas. */
    private static final int HUECO = 2;
    /** Alto reservado arriba de cada área para su título. */
    private static final int TITULO = 20;

    /** Dueño de cada bloque en la última foto. */
    private int[] duenos = new int[0];
    /** Bloques del área de archivos (el resto es swap). */
    private int bloquesArchivos;
    /** Bloque donde está el cabezal. */
    private int cabezal;
    /** Dirección del cabezal. */
    private transient DireccionCabezal direccion = DireccionCabezal.SUBE;
    /** Dice a qué archivo o proceso pertenece un dueño (para el texto emergente). */
    private transient IntFunction<String> descripcion = d -> "";

    /** Crea el mapa vacío; se llena con refrescar. */
    public PanelMapaDisco() {
        setBackground(Color.WHITE);
        setToolTipText("");
    }

    /** Toma una foto del disco y la dibuja. Debe llamarse desde el hilo de Swing con el cerrojo del cluster tomado. */
    public void refrescar(Disco disco, IntFunction<String> descripcion) {
        int total = disco.getTotalBloques();
        int[] nuevos = new int[total];
        for (int i = 0; i < total; i++) {
            nuevos[i] = disco.getDueno(i);
        }
        this.duenos = nuevos;
        this.bloquesArchivos = disco.getBloquesArchivos();
        this.cabezal = disco.getPosicion();
        this.direccion = disco.getDireccion();
        this.descripcion = descripcion;
        int anchoUtil = Math.max(LADO, getParent() == null ? 700 : getParent().getWidth() - 20);
        int columnas = Math.max(1, anchoUtil / (LADO + HUECO));
        int filasArchivos = (bloquesArchivos + columnas - 1) / columnas;
        int filasSwap = (total - bloquesArchivos + columnas - 1) / columnas;
        int alto = TITULO + filasArchivos * (LADO + HUECO) + (filasSwap > 0 ? TITULO + filasSwap * (LADO + HUECO) : 0) + 40;
        setPreferredSize(new Dimension(columnas * (LADO + HUECO) + 10, alto));
        revalidate();
        repaint();
    }

    private int columnas() {
        return Math.max(1, (Math.max(LADO, getWidth() - 10)) / (LADO + HUECO));
    }

    /** Esquina superior izquierda de la casilla del bloque dado. */
    private int[] posicionDe(int bloque, int columnas) {
        boolean esSwap = bloque >= bloquesArchivos;
        int indice = esSwap ? bloque - bloquesArchivos : bloque;
        int filasArchivos = (bloquesArchivos + columnas - 1) / columnas;
        int y0 = TITULO + (esSwap ? filasArchivos * (LADO + HUECO) + TITULO : 0);
        return new int[] {5 + (indice % columnas) * (LADO + HUECO), y0 + (indice / columnas) * (LADO + HUECO)};
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(getFont().deriveFont(Font.PLAIN, 9f));
        int columnas = columnas();
        g2.setColor(Color.DARK_GRAY);
        g2.drawString("Área de archivos (bloques 0 a " + (bloquesArchivos - 1) + ")", 5, 14);
        if (duenos.length > bloquesArchivos) {
            int filasArchivos = (bloquesArchivos + columnas - 1) / columnas;
            g2.drawString("Área de intercambio / swap (bloques " + bloquesArchivos + " a " + (duenos.length - 1) + ")",
                    5, TITULO + filasArchivos * (LADO + HUECO) + 14);
        }
        for (int b = 0; b < duenos.length; b++) {
            int[] p = posicionDe(b, columnas);
            g2.setColor(ColoresArchivos.de(duenos[b]));
            g2.fillRect(p[0], p[1], LADO, LADO);
            g2.setColor(Color.GRAY);
            g2.drawRect(p[0], p[1], LADO, LADO);
            g2.setColor(duenos[b] == 0 ? Color.GRAY : Color.WHITE);
            g2.drawString(String.valueOf(b), p[0] + 3, p[1] + 16);
        }
        if (cabezal >= 0 && cabezal < duenos.length) {
            int[] p = posicionDe(cabezal, columnas);
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRect(p[0] - 1, p[1] - 1, LADO + 2, LADO + 2);
            int[] xs = {p[0] + LADO / 2 - 5, p[0] + LADO / 2 + 5, p[0] + LADO / 2};
            int[] ys = direccion == DireccionCabezal.SUBE
                    ? new int[] {p[1] + LADO + 11, p[1] + LADO + 11, p[1] + LADO + 3}
                    : new int[] {p[1] + LADO + 3, p[1] + LADO + 3, p[1] + LADO + 11};
            g2.fillPolygon(xs, ys, 3);
        }
        g2.dispose();
    }

    /** Texto emergente: a qué pertenece el bloque sobre el que está el ratón. */
    @Override
    public String getToolTipText(MouseEvent e) {
        int columnas = columnas();
        for (int b = 0; b < duenos.length; b++) {
            int[] p = posicionDe(b, columnas);
            if (e.getX() >= p[0] && e.getX() <= p[0] + LADO && e.getY() >= p[1] && e.getY() <= p[1] + LADO) {
                String quien = duenos[b] == 0 ? "libre" : descripcion.apply(duenos[b]);
                return "Bloque " + b + ": " + quien + (b == cabezal ? " (cabezal aquí)" : "");
            }
        }
        return null;
    }
}

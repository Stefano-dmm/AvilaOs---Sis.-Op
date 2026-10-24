package avilaos.gui;

import avilaos.metricas.HistorialUtilizacion;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/** Grafico de lineas con la utilizacion de la CPU de cada computador respecto al tiempo. */
public class GraficoUtilizacion extends JPanel {

    private static final Color[] COLORES = {
        new Color(0x1F77B4), new Color(0xD62728), new Color(0x2CA02C), new Color(0xFF7F0E),
        new Color(0x9467BD), new Color(0x8C564B), new Color(0xE377C2), new Color(0x17BECF)
    };
    private static final int MARGEN_IZQ = 50;
    private static final int MARGEN_DER = 20;
    private static final int MARGEN_SUP = 34;
    private static final int MARGEN_INF = 34;
    private static final int CICLOS_MINIMOS_EJE = 20;

    private final HistorialUtilizacion historial;

    public GraficoUtilizacion(HistorialUtilizacion historial) {
        this.historial = historial;
        setPreferredSize(new Dimension(600, 260));
        setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int ancho = getWidth() - MARGEN_IZQ - MARGEN_DER;
        int alto = getHeight() - MARGEN_SUP - MARGEN_INF;
        if (ancho <= 0 || alto <= 0) {
            g2.dispose();
            return;
        }

        dibujarEjes(g2, ancho, alto);
        dibujarSeries(g2, ancho, alto);
        dibujarLeyenda(g2);
        g2.dispose();
    }

    private void dibujarEjes(Graphics2D g2, int ancho, int alto) {
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Color.DARK_GRAY);
        g2.drawString("Utilizacion de CPU por computador (ventana de "
                + HistorialUtilizacion.VENTANA + " ciclos)", MARGEN_IZQ, 16);

        for (int porcentaje = 0; porcentaje <= 100; porcentaje += 25) {
            int y = MARGEN_SUP + alto - porcentaje * alto / 100;
            g2.setColor(new Color(0xE0E0E0));
            g2.drawLine(MARGEN_IZQ, y, MARGEN_IZQ + ancho, y);
            g2.setColor(Color.DARK_GRAY);
            String texto = porcentaje + "%";
            g2.drawString(texto, MARGEN_IZQ - 8 - fm.stringWidth(texto), y + fm.getAscent() / 2 - 1);
        }

        int maxCiclo = ejeX();
        int paso = Math.max(1, maxCiclo / 10);
        for (int ciclo = 0; ciclo <= maxCiclo; ciclo += paso) {
            int x = MARGEN_IZQ + ciclo * ancho / maxCiclo;
            g2.setColor(Color.DARK_GRAY);
            g2.drawLine(x, MARGEN_SUP + alto, x, MARGEN_SUP + alto + 4);
            String texto = String.valueOf(ciclo);
            g2.drawString(texto, x - fm.stringWidth(texto) / 2, MARGEN_SUP + alto + 18);
        }
        g2.drawString("Ciclo de reloj", MARGEN_IZQ + ancho / 2 - 35, getHeight() - 4);
        g2.drawRect(MARGEN_IZQ, MARGEN_SUP, ancho, alto);
    }

    private void dibujarSeries(Graphics2D g2, int ancho, int alto) {
        int maxCiclo = ejeX();
        int puntos = historial.getCantidadPuntos();
        g2.setStroke(new BasicStroke(2f));
        for (int s = 0; s < historial.getCantidadSeries(); s++) {
            g2.setColor(COLORES[s % COLORES.length]);
            int xPrevio = 0;
            int yPrevio = 0;
            for (int k = 0; k < puntos; k++) {
                int x = MARGEN_IZQ + (k + 1) * ancho / maxCiclo;
                int y = MARGEN_SUP + alto - (int) Math.round(historial.getValor(s, k) * alto);
                if (k > 0) {
                    g2.drawLine(xPrevio, yPrevio, x, y);
                }
                xPrevio = x;
                yPrevio = y;
            }
        }
        g2.setStroke(new BasicStroke(1f));
    }

    private void dibujarLeyenda(Graphics2D g2) {
        FontMetrics fm = g2.getFontMetrics();
        int x = getWidth() - MARGEN_DER;
        for (int s = historial.getCantidadSeries() - 1; s >= 0; s--) {
            String texto = historial.getNombre(s);
            x -= fm.stringWidth(texto);
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(texto, x, 16);
            x -= 16;
            g2.setColor(COLORES[s % COLORES.length]);
            g2.fillRect(x, 8, 12, 8);
            x -= 12;
        }
    }

    private int ejeX() {
        return Math.max(CICLOS_MINIMOS_EJE, historial.getCantidadPuntos());
    }
}

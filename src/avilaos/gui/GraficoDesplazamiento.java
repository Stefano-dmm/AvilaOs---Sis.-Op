package avilaos.gui;

import avilaos.disco.PoliticaDisco;
import avilaos.disco.SerieDesplazamiento;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/**
 * Gráfico del desplazamiento acumulado del cabezal con respecto al tiempo. La línea cambia de color según la política
 * del disco que estaba en uso, así se comparan las políticas: la que recorre menos posiciones sube más despacio.
 */
public class GraficoDesplazamiento extends JPanel {

    private static final long serialVersionUID = 1L;
    /** Un color por política de disco (en el orden del enum). */
    private static final Color[] COLORES = {
        new Color(66, 133, 244), new Color(52, 168, 83), new Color(234, 67, 53), new Color(171, 71, 188)
    };
    private static final int MARGEN_IZQ = 60;
    private static final int MARGEN_DER = 20;
    private static final int MARGEN_SUP = 30;
    private static final int MARGEN_INF = 35;

    private transient SerieDesplazamiento serie = new SerieDesplazamiento();

    /** Crea el gráfico vacío. */
    public GraficoDesplazamiento() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(600, 260));
    }

    /** Cambia los datos y redibuja. Debe llamarse desde el hilo de Swing con una copia de la serie. */
    public void mostrar(SerieDesplazamiento nueva) {
        this.serie = nueva;
        repaint();
    }

    /** Menor valor "redondo" mayor o igual que el máximo, para que el eje tenga divisiones limpias. */
    private static int techo(int maximo) {
        int paso = 1;
        while (paso * 5 < maximo) {
            paso *= 10;
        }
        int techo = paso;
        while (techo < maximo) {
            techo += paso;
        }
        // Mínimo 5 para que las 5 divisiones del eje sean enteras distintas aunque todavía no haya datos.
        return Math.max(techo, 5);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int ancho = getWidth() - MARGEN_IZQ - MARGEN_DER;
        int alto = getHeight() - MARGEN_SUP - MARGEN_INF;
        int n = serie.getTamanio();
        int maxY = techo(n == 0 ? 1 : serie.getAcumulado(n - 1));
        int minX = n == 0 ? 0 : serie.getCiclo(0);
        int maxX = Math.max(minX + 1, n == 0 ? 1 : serie.getCiclo(n - 1));

        g2.setColor(Color.DARK_GRAY);
        g2.drawString("Desplazamiento acumulado del cabezal (bloques) con respecto al ciclo", MARGEN_IZQ, 16);
        for (int i = 0; i <= 5; i++) {
            int y = MARGEN_SUP + alto - alto * i / 5;
            g2.setColor(new Color(225, 225, 225));
            g2.drawLine(MARGEN_IZQ, y, MARGEN_IZQ + ancho, y);
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(String.valueOf(maxY * i / 5), 8, y + 4);
            int x = MARGEN_IZQ + ancho * i / 5;
            g2.drawString(String.valueOf(minX + (maxX - minX) * i / 5), x - 8, MARGEN_SUP + alto + 16);
        }
        g2.drawRect(MARGEN_IZQ, MARGEN_SUP, ancho, alto);
        g2.drawString("ciclo", MARGEN_IZQ + ancho / 2 - 10, MARGEN_SUP + alto + 31);

        g2.setStroke(new BasicStroke(2f));
        for (int i = 1; i < n; i++) {
            int x1 = MARGEN_IZQ + (serie.getCiclo(i - 1) - minX) * ancho / (maxX - minX);
            int x2 = MARGEN_IZQ + (serie.getCiclo(i) - minX) * ancho / (maxX - minX);
            int y1 = MARGEN_SUP + alto - serie.getAcumulado(i - 1) * alto / maxY;
            int y2 = MARGEN_SUP + alto - serie.getAcumulado(i) * alto / maxY;
            g2.setColor(COLORES[serie.getPolitica(i) % COLORES.length]);
            g2.drawLine(x1, y1, x2, y2);
        }
        g2.setStroke(new BasicStroke(1f));
        int x = MARGEN_IZQ + ancho - 4 * 95;
        PoliticaDisco[] politicas = PoliticaDisco.values();
        for (int i = 0; i < politicas.length; i++) {
            g2.setColor(COLORES[i]);
            g2.fillRect(x + i * 95, 6, 12, 8);
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(politicas[i].toString(), x + i * 95 + 16, 14);
        }
        g2.dispose();
    }
}

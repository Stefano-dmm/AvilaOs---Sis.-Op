package avilaos.gui;

import avilaos.metricas.HistorialMetricas;
import avilaos.metricas.Metrica;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import javax.swing.JPanel;

/**
 * Grafico de lineas de una metrica de rendimiento (utilizacion de la CPU, throughput, tasa de exito o
 * tiempo de espera) respecto al tiempo, con una linea por computador y otra para el sistema completo.
 * La metrica que se muestra se elige con setMetrica (selector de la pestana de metricas).
 */
public class GraficoMetricas extends JPanel {

    private static final Color[] COLORES = {
        new Color(0x1F77B4), new Color(0xD62728), new Color(0x2CA02C), new Color(0xFF7F0E),
        new Color(0x9467BD), new Color(0x8C564B), new Color(0xE377C2), new Color(0x17BECF)
    };
    private static final Color COLOR_SISTEMA = new Color(0x333333);
    private static final int MARGEN_IZQ = 56;
    private static final int MARGEN_DER = 20;
    private static final int MARGEN_SUP = 50;
    private static final int MARGEN_INF = 34;
    private static final int CICLOS_MINIMOS_EJE = 20;

    private final HistorialMetricas historial;
    private Metrica metrica = Metrica.UTILIZACION;

    /** Crea el grafico a partir del historial de metricas; empieza mostrando la utilizacion de la CPU. */
    public GraficoMetricas(HistorialMetricas historial) {
        this.historial = historial;
        setPreferredSize(new Dimension(600, 270));
        setBackground(Color.WHITE);
    }

    /** Cambia la metrica que se dibuja. */
    public void setMetrica(Metrica metrica) {
        this.metrica = metrica;
        repaint();
    }

    public Metrica getMetrica() {
        return metrica;
    }

    /** Dibuja los ejes, las series y la leyenda. Solo lee el historial: no modifica nada. */
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

        int puntos = historial.getCantidadPuntos();
        double maximoY = maximoDelEjeY(puntos);
        dibujarEjes(g2, ancho, alto, maximoY);
        dibujarSeries(g2, ancho, alto, puntos, maximoY);
        dibujarLeyenda(g2);
        g2.dispose();
    }

    /**
     * Tope del eje vertical. Las fracciones (utilizacion y tasa de exito) usan 0 a 100 %. Las demas se ajustan al
     * mayor valor dibujado, redondeado hacia arriba a 1, 2 o 5 por una potencia de diez, con un minimo para que
     * una escala casi vacia no se vea exagerada.
     */
    private double maximoDelEjeY(int puntos) {
        if (metrica.esPorcentaje()) {
            return 1.0;
        }
        double mayor = 0;
        for (int s = 0; s < historial.getCantidadSeries(); s++) {
            for (int k = 0; k < puntos; k++) {
                double v = historial.getValor(metrica, s, k);
                if (!Double.isNaN(v)) {
                    mayor = Math.max(mayor, v);
                }
            }
        }
        double minimo = (metrica == Metrica.THROUGHPUT) ? 0.1 : 1.0;
        mayor = Math.max(mayor, minimo);
        double potencia = Math.pow(10, Math.floor(Math.log10(mayor)));
        double normalizado = mayor / potencia;
        double bonito = normalizado <= 1 ? 1 : normalizado <= 2 ? 2 : normalizado <= 5 ? 5 : 10;
        return bonito * potencia;
    }

    /** Texto de una marca del eje vertical: porcentaje entero, o el numero con hasta dos decimales. */
    private String formatoY(double valor) {
        if (metrica.esPorcentaje()) {
            return Math.round(valor * 100) + "%";
        }
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(valor);
    }

    /**
     * En cuantas partes se divide el eje vertical para que cada marca sea un numero redondo (1, 2, 2.5 o 5 por una
     * potencia de diez): por ejemplo 0, 2, 4, 6, 8, 10 en lugar de 0, 2.5, 5, 7.5, 10. Los porcentajes usan 4 partes.
     */
    private int divisionesDelEjeY(double maximo) {
        if (metrica.esPorcentaje()) {
            return 4;
        }
        for (int d : new int[] {5, 4, 2}) {
            double paso = maximo / d;
            double mantisa = paso / Math.pow(10, Math.floor(Math.log10(paso)));
            for (double bueno : new double[] {1, 2, 2.5, 5}) {
                if (Math.abs(mantisa - bueno) < 1e-9) {
                    return d;
                }
            }
        }
        return 4;
    }

    /** Ejes: el de la metrica (con cuadricula) y el ciclo de reloj; el titulo es el nombre de la metrica. */
    private void dibujarEjes(Graphics2D g2, int ancho, int alto, double maximoY) {
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Color.DARK_GRAY);
        g2.drawString(metrica.getEtiqueta() + " por computador", MARGEN_IZQ, 16);

        int divisiones = divisionesDelEjeY(maximoY);
        for (int i = 0; i <= divisiones; i++) {
            int y = MARGEN_SUP + alto - i * alto / divisiones;
            g2.setColor(new Color(0xE0E0E0));
            g2.drawLine(MARGEN_IZQ, y, MARGEN_IZQ + ancho, y);
            g2.setColor(Color.DARK_GRAY);
            String texto = formatoY(maximoY * i / divisiones);
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

    /**
     * Una linea por computador y una punteada para el sistema. Un valor que todavia no se puede calcular
     * (NaN) corta la linea en lugar de dibujarla en 0.
     */
    private void dibujarSeries(Graphics2D g2, int ancho, int alto, int puntos, double maximoY) {
        int maxCiclo = ejeX();
        int ultima = historial.getCantidadSeries() - 1;
        for (int s = 0; s <= ultima; s++) {
            if (s == ultima) {
                g2.setColor(COLOR_SISTEMA);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[] {6f, 4f}, 0f));
            } else {
                g2.setColor(COLORES[s % COLORES.length]);
                g2.setStroke(new BasicStroke(2f));
            }
            boolean hayAnterior = false;
            int xPrevio = 0;
            int yPrevio = 0;
            for (int k = 0; k < puntos; k++) {
                double valor = historial.getValor(metrica, s, k);
                if (Double.isNaN(valor)) {
                    hayAnterior = false;
                    continue;
                }
                int x = MARGEN_IZQ + (k + 1) * ancho / maxCiclo;
                int y = MARGEN_SUP + alto - (int) Math.round(Math.min(valor / maximoY, 1.0) * alto);
                if (hayAnterior) {
                    g2.drawLine(xPrevio, yPrevio, x, y);
                }
                xPrevio = x;
                yPrevio = y;
                hayAnterior = true;
            }
        }
        g2.setStroke(new BasicStroke(1f));
    }

    /** Leyenda con el color de cada computador y el del sistema; pasa a una segunda fila si no cabe. */
    private void dibujarLeyenda(Graphics2D g2) {
        FontMetrics fm = g2.getFontMetrics();
        int ultima = historial.getCantidadSeries() - 1;
        int x = getWidth() - MARGEN_DER;
        int y = 16;
        for (int s = ultima; s >= 0; s--) {
            String texto = historial.getNombre(s);
            int ancho = fm.stringWidth(texto) + 16 + 12;
            if (x - ancho < MARGEN_IZQ + 260 && y == 16) {
                x = getWidth() - MARGEN_DER;
                y = 34;
            }
            x -= fm.stringWidth(texto);
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(texto, x, y);
            x -= 16;
            g2.setColor(s == ultima ? COLOR_SISTEMA : COLORES[s % COLORES.length]);
            g2.fillRect(x, y - 8, 12, 8);
            x -= 12;
        }
    }

    /** Ciclos que abarca el eje horizontal: al menos 20 para que no cambie de escala demasiado al empezar. */
    private int ejeX() {
        return Math.max(CICLOS_MINIMOS_EJE, historial.getCantidadPuntos());
    }
}

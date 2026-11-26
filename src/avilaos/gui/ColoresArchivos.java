package avilaos.gui;

import java.awt.Color;

/** Colores con que se dibujan los archivos en el mapa del disco y en la tabla de asignación: un color por archivo. */
final class ColoresArchivos {

    /** Color de un bloque libre. */
    static final Color LIBRE = new Color(235, 235, 235);
    /** Color base de los bloques de swap (cada proceso suspendido lo oscurece distinto). */
    private static final Color SWAP = new Color(214, 120, 0);

    /** Colores distinguibles entre sí para los archivos; se repiten si hay más archivos que colores. */
    private static final Color[] PALETA = {
        new Color(66, 133, 244), new Color(52, 168, 83), new Color(171, 71, 188), new Color(0, 172, 193),
        new Color(233, 30, 99), new Color(124, 179, 66), new Color(255, 112, 67), new Color(92, 107, 192),
        new Color(0, 150, 136), new Color(158, 157, 36), new Color(141, 110, 99), new Color(38, 166, 154)
    };

    /** Clase de utilidades: no se instancia. */
    private ColoresArchivos() {
    }

    /** Color del bloque según su dueño: 0 libre, positivo un archivo, negativo un proceso suspendido en el swap. */
    static Color de(int dueno) {
        if (dueno == 0) {
            return LIBRE;
        }
        if (dueno < 0) {
            int variacion = (-dueno * 25) % 80;
            return new Color(Math.max(0, SWAP.getRed() - variacion), Math.max(0, SWAP.getGreen() - variacion / 2), SWAP.getBlue());
        }
        return PALETA[(dueno - 1) % PALETA.length];
    }
}

/**
 * Interfaz grafica (Swing). Solo observa y controla el sistema: no contiene logica del sistema operativo.
 *
 * <p>Ventana de configuracion, ventana principal con la Vista 1 (crear procesos), los buffers, una vista por
 * computador (Vista 2) y las metricas. El avance lo da el reloj global en su propio hilo; la interfaz lee el
 * estado tomando el cerrojo del cluster para no ver nunca un ciclo a medias.
 */
package avilaos.gui;

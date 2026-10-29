/**
 * AvilaOS: simulador de un sistema operativo distribuido (Proyecto 1 de Sistemas Operativos).
 *
 * <p>Un cluster de computadores, cada uno con su CPU, su RAM y su propio nucleo con planificador y colas,
 * sincronizados por un reloj global. Los procesos son de un solo hilo y los productores y consumidores se
 * comunican con buffers acotados sincronizados con semaforos, con acceso local o remoto.
 *
 * <p>Se entra por {@link avilaos.gui.AvilaOSGui}. Capas, de abajo hacia arriba: {@code hardware} (reloj, CPU,
 * RAM), {@code nucleo} (computador y cluster), {@code sincronizacion} (semaforos y buffers), {@code modelo}
 * (proceso y enums) y, solo observando, {@code gui}, {@code metricas} y {@code config}. Las estructuras de
 * datos son propias ({@code estructuras}); no se usa ninguna coleccion de java.util.
 *
 * <p>Quien protege que (hilos, semaforos y colas) esta en docs/Guia_del_codigo.md.
 */
package avilaos;

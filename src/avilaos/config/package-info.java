/**
 * Configuracion de una simulacion: parametros, computadores, buffers y procesos iniciales.
 *
 * <p>{@code Configuracion} guarda y valida los datos; {@code ArchivoConfiguracion} los lee y escribe en CSV;
 * {@code LectorFormulario} convierte el texto de los formularios en datos validados (se prueba sin ventanas);
 * {@code FabricaProcesos} crea cada proceso con el programa de su tipo y {@code ConstructorCluster} arma un
 * {@code Cluster} completo desde una configuracion. Estas clases solo las usa el hilo de Swing.
 */
package avilaos.config;

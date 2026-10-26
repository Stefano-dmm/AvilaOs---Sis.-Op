package avilaos.metricas;

/**
 * Metricas de un computador o del sistema completo. Los valores que no se pueden calcular
 * todavia (por ejemplo, sin procesos terminados) son NaN.
 *
 * @param throughput procesos completados por ciclo de reloj
 * @param utilizacion fraccion (0 a 1) de ciclos en que la CPU ejecuto un proceso de usuario
 * @param respuestaPromedio ciclos promedio entre la llegada de un proceso y su primer despacho
 * @param cumplimiento fraccion (0 a 1) de procesos terminados que cumplieron su deadline
 * @param equidad indice de Jain (0 a 1) sobre la fraccion de su tiempo en el sistema que cada proceso uso la CPU
 * @param bloqueoSemaforoPromedio ciclos promedio que cada productor o consumidor ha pasado bloqueado
 *                                en colas de semaforos
 */
public record ResumenMetricas(String nombre, double throughput, double utilizacion,
                              double respuestaPromedio, double cumplimiento, double equidad,
                              double bloqueoSemaforoPromedio, int completados, int terminados) {
}

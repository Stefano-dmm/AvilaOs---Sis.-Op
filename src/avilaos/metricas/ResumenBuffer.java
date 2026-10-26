package avilaos.metricas;

/** Indicadores del productor-consumidor para un buffer. */
public record ResumenBuffer(String nombre, String anfitrion, int producidos, int consumidos,
                            int elementos, int capacidad) {
}

package avilaos.config;

/** Un buffer de la simulacion; indiceComputador es la posicion (desde 0) de su computador anfitrion. */
public record ConfigBuffer(String nombre, int capacidad, int indiceComputador) {
}

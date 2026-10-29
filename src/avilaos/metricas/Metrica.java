package avilaos.metricas;

/** Metricas de rendimiento que se pueden graficar contra el tiempo, para cada computador y para el sistema. */
public enum Metrica {

    /** Fraccion de ciclos con la CPU ocupada en una ventana movil de los ultimos ciclos. */
    UTILIZACION("Utilizacion de la CPU (ultimos 10 ciclos)", true),
    /** Procesos completados por ciclo de reloj, acumulado desde el inicio. */
    THROUGHPUT("Throughput (procesos completados por ciclo)", false),
    /** Procesos terminados a tiempo entre procesos terminados, acumulado desde el inicio. */
    TASA_EXITO("Tasa de exito (deadlines cumplidos)", true),
    /** Ciclos promedio que los procesos han esperado en la cola de listos. */
    TIEMPO_ESPERA("Tiempo de espera promedio (ciclos)", false);

    private final String etiqueta;
    private final boolean porcentaje;

    Metrica(String etiqueta, boolean porcentaje) {
        this.etiqueta = etiqueta;
        this.porcentaje = porcentaje;
    }

    /** Titulo del grafico y texto del selector. */
    public String getEtiqueta() {
        return etiqueta;
    }

    /** Verdadero si el valor es una fraccion (0 a 1) que se muestra como porcentaje con el eje fijo de 0 a 100. */
    public boolean esPorcentaje() {
        return porcentaje;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}

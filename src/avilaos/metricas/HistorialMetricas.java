package avilaos.metricas;

import avilaos.estructuras.Nodo;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;

/**
 * Guarda, ciclo a ciclo, el valor de cada {@link Metrica} para cada computador y para el sistema completo:
 * es la serie de datos que dibuja el grafico. La utilizacion viene de {@link HistorialUtilizacion}; el
 * throughput, la tasa de exito y el tiempo de espera se calculan con {@link CalculadorMetricas}.
 *
 * <p>La ultima serie es siempre "Sistema". Concurrencia: registrar() lo llama el hilo del reloj (con el
 * cerrojo del cluster tomado) y el grafico lee al pintar, en el hilo de Swing y sin ese cerrojo; por eso los
 * metodos son synchronized. Los datos se guardan en arreglos que se duplican al llenarse.
 */
public class HistorialMetricas {

    private final Cluster cluster;
    private final HistorialUtilizacion utilizacion;
    private final String[] nombres;
    /** datos[metrica][serie][ciclo]; la utilizacion no se guarda aqui (la tiene HistorialUtilizacion). */
    private double[][][] datos;
    private int ciclos;

    /** Crea un historial vacio con una serie por computador mas la del sistema. */
    public HistorialMetricas(Cluster cluster) {
        this.cluster = cluster;
        this.utilizacion = new HistorialUtilizacion(cluster);
        int n = cluster.getCantidadComputadores();
        this.nombres = new String[n + 1];
        int i = 0;
        Nodo<Computador> actual = cluster.getComputadores().getCabeza();
        while (actual != null) {
            nombres[i++] = "PC" + actual.getDato().getId() + " " + actual.getDato().getNombre();
            actual = actual.getSiguiente();
        }
        nombres[n] = "Sistema";
        this.datos = new double[Metrica.values().length][n + 1][64];
    }

    /** Toma una muestra de todas las metricas: debe llamarse una vez despues de cada ciclo del reloj. */
    public synchronized void registrar() {
        utilizacion.registrar();
        if (ciclos == datos[0][0].length) {
            for (int m = 0; m < datos.length; m++) {
                for (int s = 0; s < datos[m].length; s++) {
                    double[] ampliado = new double[ciclos * 2];
                    System.arraycopy(datos[m][s], 0, ampliado, 0, ciclos);
                    datos[m][s] = ampliado;
                }
            }
        }
        int serie = 0;
        Nodo<Computador> actual = cluster.getComputadores().getCabeza();
        while (actual != null) {
            guardar(serie++, CalculadorMetricas.deComputador(actual.getDato()));
            actual = actual.getSiguiente();
        }
        guardar(serie, CalculadorMetricas.global(cluster));
        ciclos++;
    }

    private void guardar(int serie, ResumenMetricas r) {
        datos[Metrica.THROUGHPUT.ordinal()][serie][ciclos] = r.throughput();
        datos[Metrica.TASA_EXITO.ordinal()][serie][ciclos] = r.cumplimiento();
        datos[Metrica.TIEMPO_ESPERA.ordinal()][serie][ciclos] = r.esperaPromedio();
    }

    /** Cantidad de series: un computador cada una y, al final, el sistema. */
    public int getCantidadSeries() {
        return nombres.length;
    }

    public String getNombre(int serie) {
        return nombres[serie];
    }

    /** Cantidad de ciclos registrados; solo crece, asi que los indices menores siguen siendo validos. */
    public synchronized int getCantidadPuntos() {
        return ciclos;
    }

    /**
     * Valor de la metrica para la serie en el ciclo dado (desde 0). Las fracciones van de 0 a 1. Es NaN si
     * todavia no se puede calcular (por ejemplo la tasa de exito antes de que termine el primer proceso).
     */
    public synchronized double getValor(Metrica metrica, int serie, int ciclo) {
        if (metrica == Metrica.UTILIZACION) {
            if (serie == nombres.length - 1) {
                double suma = 0;
                for (int s = 0; s < serie; s++) {
                    suma += utilizacion.getValor(s, ciclo);
                }
                return suma / serie;
            }
            return utilizacion.getValor(serie, ciclo);
        }
        return datos[metrica.ordinal()][serie][ciclo];
    }
}

package avilaos.metricas;

import avilaos.estructuras.Nodo;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;

/**
 * Guarda, por computador y por ciclo, la utilizacion de la CPU en una ventana movil
 * de los ultimos ciclos. Es la serie de datos que dibuja el grafico.
 */
public class HistorialUtilizacion {

    public static final int VENTANA = 10;

    private final Cluster cluster;
    private final String[] nombres;
    private final int[] ocupadosPrevios;
    private int[][] ocupadoPorCiclo;
    private int ciclos;

    public HistorialUtilizacion(Cluster cluster) {
        this.cluster = cluster;
        int n = cluster.getCantidadComputadores();
        this.nombres = new String[n];
        this.ocupadosPrevios = new int[n];
        this.ocupadoPorCiclo = new int[n][64];
        int i = 0;
        Nodo<Computador> actual = cluster.getComputadores().getCabeza();
        while (actual != null) {
            nombres[i++] = "PC" + actual.getDato().getId() + " " + actual.getDato().getNombre();
            actual = actual.getSiguiente();
        }
    }

    /** Toma una muestra: debe llamarse una vez despues de cada ciclo del reloj. */
    public void registrar() {
        if (ciclos == ocupadoPorCiclo[0].length) {
            for (int i = 0; i < ocupadoPorCiclo.length; i++) {
                int[] ampliado = new int[ciclos * 2];
                System.arraycopy(ocupadoPorCiclo[i], 0, ampliado, 0, ciclos);
                ocupadoPorCiclo[i] = ampliado;
            }
        }
        int i = 0;
        Nodo<Computador> actual = cluster.getComputadores().getCabeza();
        while (actual != null) {
            int ocupados = actual.getDato().getCiclosOcupados();
            ocupadoPorCiclo[i][ciclos] = ocupados - ocupadosPrevios[i];
            ocupadosPrevios[i] = ocupados;
            actual = actual.getSiguiente();
            i++;
        }
        ciclos++;
    }

    public int getCantidadSeries() {
        return nombres.length;
    }

    public String getNombre(int serie) {
        return nombres[serie];
    }

    public int getCantidadPuntos() {
        return ciclos;
    }

    /** Utilizacion (0 a 1) del computador en la ventana que termina en el ciclo indicado (desde 0). */
    public double getValor(int serie, int ciclo) {
        int desde = Math.max(0, ciclo - VENTANA + 1);
        int suma = 0;
        for (int k = desde; k <= ciclo; k++) {
            suma += ocupadoPorCiclo[serie][k];
        }
        return (double) suma / (ciclo - desde + 1);
    }
}

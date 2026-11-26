package avilaos.disco;

/**
 * Serie en el tiempo del desplazamiento acumulado del cabezal: una muestra por ciclo con el ciclo, el total
 * recorrido hasta ese momento y la política que estaba en uso. La dibuja el gráfico para comparar las políticas.
 * No es segura entre hilos: la protege el candado del Disco.
 */
public class SerieDesplazamiento {

    /** Ciclo global de cada muestra. */
    private int[] ciclos = new int[256];
    /** Desplazamiento acumulado en cada muestra. */
    private int[] acumulados = new int[256];
    /** Ordinal de la PoliticaDisco en uso en cada muestra. */
    private int[] politicas = new int[256];
    /** Cantidad de muestras guardadas. */
    private int tamanio;

    /** Agrega una muestra al final, ampliando los arreglos cuando se llenan. */
    public void agregar(int ciclo, int acumulado, int politica) {
        if (tamanio == ciclos.length) {
            ciclos = ampliar(ciclos);
            acumulados = ampliar(acumulados);
            politicas = ampliar(politicas);
        }
        ciclos[tamanio] = ciclo;
        acumulados[tamanio] = acumulado;
        politicas[tamanio] = politica;
        tamanio++;
    }

    private static int[] ampliar(int[] origen) {
        int[] nuevo = new int[origen.length * 2];
        System.arraycopy(origen, 0, nuevo, 0, origen.length);
        return nuevo;
    }

    public int getTamanio() {
        return tamanio;
    }

    public int getCiclo(int i) {
        return ciclos[i];
    }

    public int getAcumulado(int i) {
        return acumulados[i];
    }

    public int getPolitica(int i) {
        return politicas[i];
    }

    /** Copia independiente de la serie, para que otro hilo la lea sin que cambie. */
    public SerieDesplazamiento copiar() {
        SerieDesplazamiento copia = new SerieDesplazamiento();
        copia.ciclos = ciclos.clone();
        copia.acumulados = acumulados.clone();
        copia.politicas = politicas.clone();
        copia.tamanio = tamanio;
        return copia;
    }
}

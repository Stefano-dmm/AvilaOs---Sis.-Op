package avilaos.pruebas;

import avilaos.disco.Disco;
import avilaos.disco.OperacionES;
import avilaos.disco.PoliticaDisco;
import avilaos.disco.SolicitudES;

/**
 * Experimento que compara las cuatro políticas de planificación del disco con las mismas colas de solicitudes. No usa
 * hilos ni interfaz: cada corrida encola todo junto, avanza el reloj del disco hasta vaciarlo y mide el desplazamiento
 * del cabezal y la espera. Imprime las tablas en Markdown para pegarlas en docs/resultados_disco.md.
 */
public final class ExperimentoDisco {

    private static final int BLOQUES = 200;
    private static final int CABEZAL_INICIAL = 50;
    private static final int VELOCIDAD = 5;
    private static final int SEMILLAS = 20;
    private static final int[] TAMANIOS = {8, 16, 32, 64};

    private ExperimentoDisco() {
    }

    /** Resultado de una corrida. */
    private record Corrida(String orden, int desplazamiento, double espera, int ciclos) {
    }

    /** Encola los bloques en orden de llegada y avanza el disco hasta que quede libre. */
    private static Corrida correr(PoliticaDisco politica, int[] bloques) {
        Disco disco = new Disco(BLOQUES, 0, CABEZAL_INICIAL, VELOCIDAD, politica);
        StringBuilder orden = new StringBuilder();
        int[] ultimo = {0};
        disco.setOyente((s, ciclo) -> {
            orden.append(orden.length() == 0 ? "" : ",").append(s.getBloque());
            ultimo[0] = ciclo;
        });
        for (int b : bloques) {
            disco.encolar(new SolicitudES(OperacionES.LEER, b, 1, 1, "P1-x", "/x", 1));
        }
        int ciclo = 1;
        while ((disco.getTamanioCola() > 0 || disco.getEnServicio() != null) && ciclo < 100000) {
            disco.tic(ciclo++);
        }
        return new Corrida(orden.toString(), disco.getDesplazamientoTotal(), disco.getEsperaPromedio(), ultimo[0]);
    }

    /** Generador congruencial lineal: mismas semillas, mismas colas, así el experimento se puede repetir. */
    private static int[] colaAleatoria(int tamanio, long semilla) {
        long estado = semilla * 6364136223846793005L + 1442695040888963407L;
        int[] cola = new int[tamanio];
        for (int i = 0; i < tamanio; i++) {
            estado = estado * 6364136223846793005L + 1442695040888963407L;
            cola[i] = (int) ((estado >>> 33) % BLOQUES);
        }
        return cola;
    }

    private static void casoFijo(String titulo, int[] cola) {
        System.out.println("### " + titulo + "\n");
        System.out.println("Cola de llegada: " + texto(cola) + " (cabezal en " + CABEZAL_INICIAL + ")\n");
        System.out.println("| Política | Orden de atención | Desplazamiento (bloques) | Espera promedio (ciclos) | Ciclos totales |");
        System.out.println("|---|---|---:|---:|---:|");
        for (PoliticaDisco p : PoliticaDisco.values()) {
            Corrida c = correr(p, cola);
            System.out.printf("| %s | %s | %d | %.2f | %d |%n", p, c.orden(), c.desplazamiento(), c.espera(), c.ciclos());
        }
        System.out.println();
    }

    private static void colasAleatorias() {
        System.out.println("### Colas aleatorias (promedio de " + SEMILLAS + " colas por tamaño)\n");
        System.out.println("| Solicitudes | Política | Desplazamiento promedio | Espera promedio (ciclos) | Ciclos totales promedio |");
        System.out.println("|---:|---|---:|---:|---:|");
        for (int n : TAMANIOS) {
            for (PoliticaDisco p : PoliticaDisco.values()) {
                double desp = 0;
                double esp = 0;
                double cic = 0;
                for (int semilla = 1; semilla <= SEMILLAS; semilla++) {
                    Corrida c = correr(p, colaAleatoria(n, semilla));
                    desp += c.desplazamiento();
                    esp += c.espera();
                    cic += c.ciclos();
                }
                System.out.printf("| %d | %s | %.1f | %.2f | %.1f |%n", n, p, desp / SEMILLAS, esp / SEMILLAS, cic / SEMILLAS);
            }
        }
        System.out.println();
    }

    private static String texto(int[] cola) {
        StringBuilder sb = new StringBuilder();
        for (int b : cola) {
            sb.append(sb.length() == 0 ? "" : ", ").append(b);
        }
        return sb.toString();
    }

    /** Punto de entrada: imprime las tablas del experimento. */
    public static void main(String[] args) {
        System.out.println("## Resultados del experimento\n");
        System.out.println("Disco de " + BLOQUES + " bloques, cabezal inicial en " + CABEZAL_INICIAL + ", velocidad " + VELOCIDAD
                + " posiciones por ciclo, 1 ciclo por bloque transferido. En C-SCAN el desplazamiento no cuenta el salto de regreso.\n");
        casoFijo("Casos P1 a P4 del enunciado", new int[] {95, 180, 34, 119, 11, 123, 62, 64});
        casoFijo("Ejemplo del anexo", new int[] {82, 17, 43, 140, 24, 16, 190});
        colasAleatorias();
    }
}

package avilaos.pruebas;

import avilaos.metricas.CalculadorMetricas;
import avilaos.metricas.ResumenMetricas;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.sincronizacion.BufferSO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Experimentos para las conclusiones del informe. Ejecutar con
 * {@code java avilaos.pruebas.ExperimentoPoliticas [archivo.md]}: imprime las tablas y, si se indica
 * un archivo, tambien las guarda ahi.
 */
public final class ExperimentoPoliticas {

    private static final int LIMITE_CICLOS = 500;

    /** nombre, instrucciones, prioridad, deadline, ciclo de llegada. */
    private static final Object[][] CARGA = {
        {"P1", 8, 1, 60, 0},
        {"P2", 3, 3, 8, 0},
        {"P3", 6, 2, 40, 0},
        {"P4", 2, 5, 6, 1},
        {"P5", 5, 1, 30, 2},
        {"P6", 4, 4, 14, 3},
        {"P7", 7, 2, 50, 4},
        {"P8", 1, 3, 5, 5},
    };

    private ExperimentoPoliticas() {
    }

    public static void main(String[] args) throws IOException {
        StringBuilder salida = new StringBuilder();
        politicas(salida);
        quantum(salida);
        productorConsumidor(salida);
        capacidadDelBuffer(salida);
        System.out.print(salida);
        if (args.length > 0) {
            Files.writeString(Path.of(args[0]), salida.toString(), StandardCharsets.UTF_8);
        }
    }

    private static String num(double valor) {
        return Double.isNaN(valor) ? "-" : String.format(java.util.Locale.ROOT, "%.2f", valor);
    }

    private static String pct(double valor) {
        return Double.isNaN(valor) ? "-" : String.format(java.util.Locale.ROOT, "%.0f%%", valor * 100);
    }

    /** Corre la misma carga en un solo computador con la politica dada y devuelve sus metricas. */
    private static Object[] correrCarga(PoliticaPlanificacion politica, int quantum) {
        Computador pc = PruebasPoliticas.computador(politica, quantum);
        Cluster cluster = pc.getCluster();
        Proceso[] procesos = new Proceso[CARGA.length];
        for (int i = 0; i < CARGA.length; i++) {
            procesos[i] = PruebasPoliticas.proceso((String) CARGA[i][0], (Integer) CARGA[i][1],
                    (Integer) CARGA[i][2], (Integer) CARGA[i][3]);
        }
        int cambios = 0;
        int fin = 0;
        for (int ciclo = 1; ciclo <= LIMITE_CICLOS; ciclo++) {
            for (int i = 0; i < CARGA.length; i++) {
                if ((Integer) CARGA[i][4] == ciclo - 1) {
                    pc.recibirProceso(procesos[i]);
                }
            }
            Proceso antes = pc.getProcesoEnCPU();
            cluster.onTickGlobal(ciclo);
            if (antes != null && pc.getProcesoEnCPU() != antes && antes.getEstado() == EstadoProceso.LISTO) {
                cambios++;
            }
            boolean todos = ciclo >= 5;
            for (Proceso p : procesos) {
                if (p.getEstado() != EstadoProceso.TERMINADO) {
                    todos = false;
                }
            }
            if (todos) {
                fin = ciclo;
                break;
            }
        }
        return new Object[] {CalculadorMetricas.deComputador(pc), fin, cambios, procesos};
    }

    private static void politicas(StringBuilder s) {
        s.append("## Experimento 1: las cuatro politicas con la misma carga\n\n");
        s.append("Un computador, 8 procesos de CPU (instrucciones 8,3,6,2,5,4,7,1) que llegan entre los ciclos 0 y 5, ")
                .append("con prioridades y deadlines distintos (algunos muy ajustados). Round Robin con quantum 3.\n\n");
        s.append("| Politica | Terminan todos | Throughput | Respuesta prom. | Cumplimiento | Equidad | Cambios de proceso |\n");
        s.append("|---|---|---|---|---|---|---|\n");
        PoliticaPlanificacion[] todas = {PoliticaPlanificacion.FCFS, PoliticaPlanificacion.EDF,
            PoliticaPlanificacion.ROUND_ROBIN, PoliticaPlanificacion.PRIORIDADES};
        for (PoliticaPlanificacion politica : todas) {
            Object[] r = correrCarga(politica, 3);
            ResumenMetricas m = (ResumenMetricas) r[0];
            s.append("| ").append(politica).append(" | ciclo ").append(r[1]).append(" | ")
                    .append(num(m.throughput())).append(" | ").append(num(m.respuestaPromedio())).append(" | ")
                    .append(pct(m.cumplimiento())).append(" (").append(m.completados()).append(" de ")
                    .append(m.terminados()).append(") | ").append(num(m.equidad())).append(" | ")
                    .append(r[2]).append(" |\n");
        }
        s.append('\n');
        s.append("Procesos que vencen su deadline, por politica:\n\n");
        for (PoliticaPlanificacion politica : todas) {
            Proceso[] procesos = (Proceso[]) correrCarga(politica, 3)[3];
            StringBuilder muertos = new StringBuilder();
            for (Proceso p : procesos) {
                if (!p.cumplioDeadline()) {
                    muertos.append(muertos.length() == 0 ? "" : ", ").append(p.getNombre());
                }
            }
            s.append("- ").append(politica).append(": ").append(muertos.length() == 0 ? "ninguno" : muertos).append('\n');
        }
        s.append('\n');
    }

    private static void quantum(StringBuilder s) {
        s.append("## Experimento 2: efecto del quantum en Round Robin\n\n");
        s.append("| Quantum | Respuesta prom. | Cumplimiento | Equidad | Cambios de proceso |\n|---|---|---|---|---|\n");
        for (int q : new int[] {1, 2, 3, 5, 8}) {
            Object[] r = correrCarga(PoliticaPlanificacion.ROUND_ROBIN, q);
            ResumenMetricas m = (ResumenMetricas) r[0];
            s.append("| ").append(q).append(" | ").append(num(m.respuestaPromedio())).append(" | ")
                    .append(pct(m.cumplimiento())).append(" | ").append(num(m.equidad())).append(" | ")
                    .append(r[2]).append(" |\n");
        }
        s.append('\n');
    }

    /** Un productor y un consumidor de 12 elementos sobre un buffer; el consumidor puede ser local o remoto. */
    private static Object[] correrComunicacion(int latencia, boolean remoto, int capacidad) {
        Cluster cluster = PruebasIntegracion.cluster(2, 200, latencia);
        Computador anfitrion = cluster.obtenerPorIndice(0);
        Computador otro = remoto ? cluster.obtenerPorIndice(1) : anfitrion;
        BufferSO b = cluster.crearBuffer("B1", capacidad, anfitrion.getId());
        Proceso prod = PruebasIntegracion.comunicante("prod", b, true, 1, 12, 100000);
        Proceso cons = PruebasIntegracion.comunicante("cons", b, false, 1, 12, 100000);
        anfitrion.recibirProceso(prod);
        otro.recibirProceso(cons);
        int fin = PruebasIntegracion.correrHasta(cluster, prod, cons);
        ResumenMetricas m = CalculadorMetricas.global(cluster);
        return new Object[] {fin, m, prod.getCiclosBloqueadoSemaforo() + cons.getCiclosBloqueadoSemaforo()};
    }

    private static void productorConsumidor(StringBuilder s) {
        s.append("## Experimento 3: productor y consumidor locales y remotos\n\n");
        s.append("Un productor y un consumidor de 12 elementos, buffer de capacidad 2. En el caso local los dos ")
                .append("estan en el mismo computador que el buffer; en el remoto, el consumidor esta en otro.\n\n");
        s.append("| Caso | Latencia de red | Terminan en el ciclo | Bloqueo en semaforos (prom. ciclos) | Utilizacion CPU del sistema |\n");
        s.append("|---|---|---|---|---|\n");
        Object[] local = correrComunicacion(0, false, 2);
        ResumenMetricas ml = (ResumenMetricas) local[1];
        s.append("| Local | - | ").append(local[0]).append(" | ").append(num(ml.bloqueoSemaforoPromedio()))
                .append(" | ").append(pct(ml.utilizacion())).append(" |\n");
        for (int latencia : new int[] {0, 2, 5, 10}) {
            Object[] r = correrComunicacion(latencia, true, 2);
            ResumenMetricas m = (ResumenMetricas) r[1];
            s.append("| Remoto | ").append(latencia).append(" | ").append(r[0]).append(" | ")
                    .append(num(m.bloqueoSemaforoPromedio())).append(" | ").append(pct(m.utilizacion())).append(" |\n");
        }
        s.append('\n');
    }

    private static void capacidadDelBuffer(StringBuilder s) {
        s.append("## Experimento 4: capacidad del buffer\n\n");
        s.append("El mismo productor y consumidor locales (12 elementos) con buffers de distinta capacidad.\n\n");
        s.append("| Capacidad | Terminan en el ciclo | Bloqueo en semaforos (prom. ciclos) |\n|---|---|---|\n");
        for (int capacidad : new int[] {1, 2, 4, 8}) {
            Object[] r = correrComunicacion(0, false, capacidad);
            ResumenMetricas m = (ResumenMetricas) r[1];
            s.append("| ").append(capacidad).append(" | ").append(r[0]).append(" | ")
                    .append(num(m.bloqueoSemaforoPromedio())).append(" |\n");
        }
        s.append('\n');
    }
}

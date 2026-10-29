package avilaos.pruebas;

import static avilaos.pruebas.Verificador.casi;
import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.gui.GraficoMetricas;
import avilaos.metricas.CalculadorMetricas;
import avilaos.metricas.HistorialMetricas;
import avilaos.metricas.HistorialUtilizacion;
import avilaos.metricas.Metrica;
import avilaos.metricas.ResumenBuffer;
import avilaos.metricas.ResumenMetricas;
import avilaos.modelo.Proceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.sincronizacion.BufferSO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Pruebas de las metricas de rendimiento con escenarios cuyos valores se calculan a mano. */
public final class PruebasMetricas {

    private PruebasMetricas() {
    }

    public static void ejecutar() {
        metricasDeRendimiento();
        productorConsumidor();
        tiempoDeEsperaYGrafico();
    }

    /** Mismo escenario de metricasDeRendimiento: A y B en PC1 (FCFS) y C (muere por deadline) y D en PC2. */
    private static Cluster escenarioDeDosComputadores() {
        Cluster cluster = PruebasIntegracion.cluster(2, 100, 0);
        cluster.obtenerPorIndice(0).recibirProceso(PruebasIntegracion.cpu("A", 3, 40));
        cluster.obtenerPorIndice(0).recibirProceso(PruebasIntegracion.cpu("B", 3, 40));
        cluster.obtenerPorIndice(1).recibirProceso(PruebasIntegracion.cpu("C", 9, 3));
        cluster.obtenerPorIndice(1).recibirProceso(PruebasIntegracion.cpu("D", 2, 40));
        return cluster;
    }

    private static void tiempoDeEsperaYGrafico() {
        seccion("Tiempo de espera y grafico de metricas");
        Cluster cluster = escenarioDeDosComputadores();
        HistorialMetricas historial = new HistorialMetricas(cluster);
        for (int ciclo = 1; ciclo <= 14; ciclo++) {
            cluster.onTickGlobal(ciclo);
            historial.registrar();
        }
        Computador pc1 = cluster.obtenerPorIndice(0);
        Computador pc2 = cluster.obtenerPorIndice(1);

        // A ocupa la CPU desde el primer ciclo (espera 0) y B espera los 3 ciclos que A ejecuta (espera 3).
        casi(1.5, CalculadorMetricas.deComputador(pc1).esperaPromedio(), "PC1 tiempo de espera promedio: A espera 0 y B espera 3");
        casi(1.5, CalculadorMetricas.deComputador(pc2).esperaPromedio(), "PC2 tiempo de espera promedio: C espera 0 y D espera 3");
        casi(1.5, CalculadorMetricas.global(cluster).esperaPromedio(), "sistema tiempo de espera promedio: (0 + 3 + 0 + 3) / 4");
        casi(3, ((Proceso) pc1.getColaTerminados().getLista().obtener(1)).getCiclosEnListos(), "B acumulo 3 ciclos en la cola de listos");
        casi(0, ((Proceso) pc1.getColaTerminados().getLista().obtener(0)).getCiclosEnListos(), "A, que fue el primero, no espero nada");

        igual(3, historial.getCantidadSeries(), "hay una serie por computador y otra para el sistema");
        igual("Sistema", historial.getNombre(2), "la ultima serie es la del sistema");
        igual(14, historial.getCantidadPuntos(), "el historial guardo una muestra por ciclo");

        casi(0.0, historial.getValor(Metrica.THROUGHPUT, 0, 0), "al ciclo 1 PC1 aun no completo ningun proceso");
        casi(1.0 / 3, historial.getValor(Metrica.THROUGHPUT, 0, 2), "al ciclo 3 PC1 completo 1 proceso en 3 ciclos");
        casi(2.0 / 14, historial.getValor(Metrica.THROUGHPUT, 0, 13), "al ciclo 14 PC1 completo 2 procesos en 14 ciclos");
        casi(3.0 / 14, historial.getValor(Metrica.THROUGHPUT, 2, 13), "al ciclo 14 el sistema completo 3 procesos en 14 ciclos");

        casi(Double.NaN, historial.getValor(Metrica.TASA_EXITO, 0, 0), "antes de terminar el primer proceso la tasa de exito no esta definida");
        casi(1.0, historial.getValor(Metrica.TASA_EXITO, 0, 2), "al ciclo 3 PC1 cumplio el deadline de su unico terminado");
        casi(0.0, historial.getValor(Metrica.TASA_EXITO, 1, 2), "al ciclo 3 C de PC2 vencio su deadline: tasa de exito 0");
        casi(0.5, historial.getValor(Metrica.TASA_EXITO, 1, 13), "al ciclo 14 PC2 cumplio 1 de 2 deadlines");
        casi(0.75, historial.getValor(Metrica.TASA_EXITO, 2, 13), "al ciclo 14 el sistema cumplio 3 de 4 deadlines");

        casi(0.5, historial.getValor(Metrica.TIEMPO_ESPERA, 0, 0), "al ciclo 1 solo B espera: (0 + 1) / 2");
        casi(1.5, historial.getValor(Metrica.TIEMPO_ESPERA, 0, 2), "al ciclo 3 B ya espero 3: (0 + 3) / 2");
        casi(1.5, historial.getValor(Metrica.TIEMPO_ESPERA, 2, 13), "el sistema termina con espera promedio 1.5");

        casi(0.2, historial.getValor(Metrica.UTILIZACION, 0, 13), "la utilizacion de PC1 sigue viniendo de la ventana de 10 ciclos");
        casi(0.15, historial.getValor(Metrica.UTILIZACION, 2, 13), "la utilizacion del sistema es el promedio de los computadores");

        verificar(Metrica.UTILIZACION.esPorcentaje() && Metrica.TASA_EXITO.esPorcentaje(), "utilizacion y tasa de exito son porcentajes");
        verificar(!Metrica.THROUGHPUT.esPorcentaje() && !Metrica.TIEMPO_ESPERA.esPorcentaje(), "throughput y tiempo de espera no lo son");
        igual(4, Metrica.values().length, "son las cuatro metricas que pide la rubrica");

        GraficoMetricas grafico = new GraficoMetricas(historial);
        grafico.setSize(700, 300);
        for (Metrica metrica : Metrica.values()) {
            grafico.setMetrica(metrica);
            BufferedImage imagen = new BufferedImage(700, 300, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = imagen.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 700, 300);
            boolean dibujo = true;
            try {
                grafico.paint(g);
            } catch (RuntimeException ex) {
                dibujo = false;
            }
            g.dispose();
            int conTinta = 0;
            for (int x = 0; x < 700; x += 3) {
                for (int y = 0; y < 300; y += 3) {
                    if (imagen.getRGB(x, y) != Color.WHITE.getRGB()) {
                        conTinta++;
                    }
                }
            }
            verificar(dibujo && conTinta > 200, "el grafico dibuja la metrica: " + metrica.getEtiqueta());
        }
        igual(Metrica.TIEMPO_ESPERA, grafico.getMetrica(), "el grafico recuerda la metrica elegida");
    }

    private static void metricasDeRendimiento() {
        seccion("Metricas de rendimiento (escenario calculado a mano)");
        Cluster cluster = PruebasIntegracion.cluster(2, 100, 0);
        Computador pc1 = cluster.obtenerPorIndice(0);
        Computador pc2 = cluster.obtenerPorIndice(1);
        // PC1 (FCFS): A y B de 3 instrucciones. PC2: C de 9 instrucciones con deadline 3 (no alcanza) y D de 2.
        pc1.recibirProceso(PruebasIntegracion.cpu("A", 3, 40));
        pc1.recibirProceso(PruebasIntegracion.cpu("B", 3, 40));
        pc2.recibirProceso(PruebasIntegracion.cpu("C", 9, 3));
        pc2.recibirProceso(PruebasIntegracion.cpu("D", 2, 40));
        HistorialUtilizacion historial = new HistorialUtilizacion(cluster);
        for (int ciclo = 1; ciclo <= 14; ciclo++) {
            cluster.onTickGlobal(ciclo);
            historial.registrar();
        }

        ResumenMetricas m1 = CalculadorMetricas.deComputador(pc1);
        casi(2.0 / 14, m1.throughput(), "PC1 throughput: 2 procesos completados en 14 ciclos");
        casi(6.0 / 14, m1.utilizacion(), "PC1 utilizacion: la CPU trabajo 6 de 14 ciclos");
        casi(2.0, m1.respuestaPromedio(), "PC1 respuesta promedio: A espera 1 ciclo y B espera 3");
        casi(1.0, m1.cumplimiento(), "PC1 cumplimiento: los dos cumplieron su deadline");
        casi(0.9, m1.equidad(), "PC1 equidad de Jain: A uso 100% de su tiempo y B 50%");
        igual(2, m1.completados(), "PC1 completados");
        igual(2, m1.terminados(), "PC1 terminados");

        ResumenMetricas m2 = CalculadorMetricas.deComputador(pc2);
        casi(1.0 / 14, m2.throughput(), "PC2 throughput: solo D se completa");
        casi(5.0 / 14, m2.utilizacion(), "PC2 utilizacion: 3 ciclos de C y 2 de D");
        casi(0.5, m2.cumplimiento(), "PC2 cumplimiento: C venció su deadline y D no");
        casi(1.96 / 2.32, m2.equidad(), "PC2 equidad de Jain: C uso 100% de su tiempo y D 40%");
        igual(2, m2.terminados(), "PC2 terminados incluye al proceso terminado por deadline");

        ResumenMetricas global = CalculadorMetricas.global(cluster);
        casi(3.0 / 14, global.throughput(), "sistema throughput: 3 completados en 14 ciclos de reloj");
        casi(11.0 / 28, global.utilizacion(), "sistema utilizacion: 11 ciclos de CPU de 28 posibles");
        casi(2.0, global.respuestaPromedio(), "sistema respuesta promedio");
        casi(0.75, global.cumplimiento(), "sistema cumplimiento: 3 de 4");
        casi(Double.NaN, global.bloqueoSemaforoPromedio(), "sin productores ni consumidores no hay tiempo bloqueado que medir");

        igual(14, historial.getCantidadPuntos(), "el historial guardo una muestra por ciclo");
        casi(1.0, historial.getValor(0, 5), "PC1 al ciclo 6: utilizacion 100% en la ventana");
        casi(0.6, historial.getValor(0, 9), "PC1 al ciclo 10: 6 de los ultimos 10 ciclos");
        casi(0.2, historial.getValor(0, 13), "PC1 al ciclo 14: 2 de los ultimos 10 ciclos");
        casi(0.1, historial.getValor(1, 13), "PC2 al ciclo 14: 1 de los ultimos 10 ciclos");
        igual(2, historial.getCantidadSeries(), "hay una serie por computador");

        Cluster vacio = PruebasIntegracion.cluster(2, 100, 0);
        ResumenMetricas sinDatos = CalculadorMetricas.global(vacio);
        casi(Double.NaN, sinDatos.throughput(), "antes de simular el throughput no esta definido");
        casi(Double.NaN, sinDatos.equidad(), "antes de simular la equidad no esta definida");
    }

    private static void productorConsumidor() {
        seccion("Metricas del productor-consumidor");
        Cluster cluster = PruebasIntegracion.cluster(2, 100, 2);
        Computador pc1 = cluster.obtenerPorIndice(0);
        Computador pc2 = cluster.obtenerPorIndice(1);
        BufferSO b = cluster.crearBuffer("B1", 1, pc1.getId());
        Proceso productor = PruebasIntegracion.comunicante("prod", b, true, 1, 3, 5000);
        Proceso consumidor = PruebasIntegracion.comunicante("cons", b, false, 1, 3, 5000);
        pc1.recibirProceso(productor);
        pc2.recibirProceso(consumidor);

        ResumenBuffer[] antes = CalculadorMetricas.deBuffers(cluster);
        igual(1, antes.length, "hay un resumen por buffer");
        igual(0, antes[0].producidos(), "al empezar no se ha producido nada");

        int fin = PruebasIntegracion.correrHasta(cluster, productor, consumidor);
        verificar(fin > 0, "el productor y el consumidor remoto terminan (ciclo " + fin + ")");
        ResumenBuffer r = CalculadorMetricas.deBuffers(cluster)[0];
        igual("B1", r.nombre(), "el resumen lleva el nombre del buffer");
        igual("PC1", r.anfitrion(), "y su computador anfitrion");
        igual(3, r.producidos(), "elementos producidos");
        igual(3, r.consumidos(), "elementos consumidos");
        igual(0, r.elementos(), "el buffer termina vacio");
        igual(1, r.capacidad(), "y conserva su capacidad");

        double esperadoGlobal = (productor.getCiclosBloqueadoSemaforo() + consumidor.getCiclosBloqueadoSemaforo()) / 2.0;
        casi(esperadoGlobal, CalculadorMetricas.global(cluster).bloqueoSemaforoPromedio(),
                "el tiempo bloqueado en semaforos del sistema es el promedio de los dos procesos");
        casi(productor.getCiclosBloqueadoSemaforo(), CalculadorMetricas.deComputador(pc1).bloqueoSemaforoPromedio(),
                "el tiempo bloqueado de PC1 es el de su productor");
        casi(consumidor.getCiclosBloqueadoSemaforo(), CalculadorMetricas.deComputador(pc2).bloqueoSemaforoPromedio(),
                "el tiempo bloqueado de PC2 es el de su consumidor");
        verificar(productor.getCiclosBloqueadoSemaforo() + consumidor.getCiclosBloqueadoSemaforo() > 0,
                "con un buffer de 1 y dos procesos alguien tuvo que esperar en un semaforo");
    }
}

package avilaos.pruebas;

import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.estructuras.Nodo;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.nucleo.politicas.FabricaPoliticas;
import avilaos.nucleo.programas.ProgramaCpuBound;
import avilaos.nucleo.programas.ProgramaIoBound;
import avilaos.nucleo.programas.ProgramaProductorConsumidor;
import avilaos.sincronizacion.BufferSO;

/** Simulaciones completas con el ciclo de reloj: los programas, el planificador y los bloqueos juntos. */
public final class PruebasIntegracion {

    private static final int LIMITE_CICLOS = 2000;

    private PruebasIntegracion() {
    }

    static Cluster cluster(int computadores, int ram, int latencia) {
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();
        Cluster cluster = new Cluster();
        for (int i = 1; i <= computadores; i++) {
            Computador pc = new Computador("PC" + i, ram, 2);
            pc.setPolitica(FabricaPoliticas.crear(PoliticaPlanificacion.FCFS), PoliticaPlanificacion.FCFS);
            cluster.agregarComputador(pc);
        }
        cluster.setLatenciaRed(latencia);
        return cluster;
    }

    static Proceso comunicante(String nombre, BufferSO buffer, boolean productor, int ciclos, int elementos, int deadline) {
        Proceso p = new Proceso(nombre, productor ? TipoProceso.PRODUCTOR : TipoProceso.CONSUMIDOR, 1, 10, deadline,
                ProgramaProductorConsumidor.instruccionesTotales(ciclos, elementos));
        p.setPrograma(new ProgramaProductorConsumidor(productor, buffer, ciclos));
        return p;
    }

    static Proceso cpu(String nombre, int instrucciones, int deadline) {
        Proceso p = new Proceso(nombre, TipoProceso.CPU_BOUND, 1, 10, deadline, instrucciones);
        p.setPrograma(new ProgramaCpuBound());
        return p;
    }

    private static boolean todosTerminados(Proceso... procesos) {
        for (Proceso p : procesos) {
            if (p.getEstado() != EstadoProceso.TERMINADO) {
                return false;
            }
        }
        return true;
    }

    /** Avanza el reloj hasta que terminen todos; devuelve el ciclo en que lo hicieron (o -1 si se atasco). */
    static int correrHasta(Cluster cluster, Proceso... procesos) {
        for (int ciclo = 1; ciclo <= LIMITE_CICLOS; ciclo++) {
            cluster.onTickGlobal(ciclo);
            if (todosTerminados(procesos)) {
                return ciclo;
            }
        }
        return -1;
    }

    public static void ejecutar() {
        productorConsumidorLocal();
        variosProductoresYConsumidores();
        remotoEsMasLentoQueLocal();
        deadlineMataAUnProcesoListo();
        deadlineLiberaAUnBloqueadoEnSemaforo();
        esLiberaLaCpuParaOtros();
    }

    private static void productorConsumidorLocal() {
        seccion("Productor y consumidor locales");
        Cluster cluster = cluster(2, 100, 0);
        Computador pc = cluster.obtenerPorIndice(0);
        BufferSO b = cluster.crearBuffer("B1", 2, pc.getId());
        Proceso prod = comunicante("prod", b, true, 1, 4, 1000);
        Proceso cons = comunicante("cons", b, false, 1, 4, 1000);
        pc.recibirProceso(cons);
        pc.recibirProceso(prod);
        int fin = correrHasta(cluster, prod, cons);
        verificar(fin > 0, "terminan los dos sin interbloqueo (ciclo " + fin + ")");
        igual(4, b.getProducidos(), "se produjeron 4 elementos");
        igual(4, b.getConsumidos(), "se consumieron 4 elementos");
        igual(0, b.getElementos(), "el buffer quedo vacio");
        igual(2, b.getVacios().getValor(), "vacios volvio a la capacidad");
        igual(0, b.getLlenos().getValor(), "llenos volvio a 0");
        igual(1, b.getMutex().getValor(), "el mutex quedo libre");
        igual(0, pc.getColaBloqueados().getTamanio(), "no quedan procesos bloqueados");
        verificar(cons.getCiclosBloqueadoSemaforo() > 0,
                "el consumidor, que llego primero a un buffer vacio, paso ciclos bloqueado en el semaforo");
        igual(100 - 0, pc.getRamLibre() + b.getMemoria(), "al terminar solo queda ocupada la RAM del buffer");
    }

    private static void variosProductoresYConsumidores() {
        seccion("Varios productores y consumidores sobre un buffer pequeno");
        Cluster cluster = cluster(2, 200, 2);
        Computador pc1 = cluster.obtenerPorIndice(0);
        Computador pc2 = cluster.obtenerPorIndice(1);
        BufferSO b = cluster.crearBuffer("B1", 1, pc1.getId());
        Proceso p1 = comunicante("p1", b, true, 2, 3, 5000);
        Proceso p2 = comunicante("p2", b, true, 1, 3, 5000);
        Proceso c1 = comunicante("c1", b, false, 1, 3, 5000);
        Proceso c2 = comunicante("c2", b, false, 3, 3, 5000);
        pc1.recibirProceso(p1);
        pc1.recibirProceso(c1);
        pc2.recibirProceso(p2);
        pc2.recibirProceso(c2);
        int fin = correrHasta(cluster, p1, p2, c1, c2);
        verificar(fin > 0, "los 4 procesos de dos computadores terminan (ciclo " + fin + ")");
        igual(6, b.getProducidos(), "se produjeron 6 elementos");
        igual(6, b.getConsumidos(), "se consumieron 6 elementos");
        igual(1, b.getVacios().getValor(), "vacios quedo en su valor inicial");
        igual(0, b.getLlenos().getValor(), "llenos quedo en 0");
        igual(1, b.getMutex().getValor(), "el mutex quedo libre");
    }

    private static int cicloFinal(int latencia, boolean remoto) {
        Cluster cluster = cluster(2, 100, latencia);
        Computador anfitrion = cluster.obtenerPorIndice(0);
        Computador otro = remoto ? cluster.obtenerPorIndice(1) : anfitrion;
        BufferSO b = cluster.crearBuffer("B1", 2, anfitrion.getId());
        Proceso prod = comunicante("prod", b, true, 1, 4, 5000);
        Proceso cons = comunicante("cons", b, false, 1, 4, 5000);
        anfitrion.recibirProceso(cons);
        otro.recibirProceso(prod);
        return correrHasta(cluster, prod, cons);
    }

    private static void remotoEsMasLentoQueLocal() {
        seccion("Productor remoto frente a productor local");
        int local = cicloFinal(5, false);
        int remoto = cicloFinal(5, true);
        verificar(local > 0 && remoto > 0, "ambos escenarios terminan (local " + local + ", remoto " + remoto + ")");
        verificar(remoto > local, "con latencia 5 el productor remoto tarda mas que el local");
        int sinLatencia = cicloFinal(0, true);
        verificar(sinLatencia < remoto, "con latencia 0 el remoto tarda menos que con latencia 5");
    }

    private static void deadlineMataAUnProcesoListo() {
        seccion("El deadline termina a los procesos aunque esten esperando");
        Cluster cluster = cluster(2, 100, 0);
        StringBuilder eventos = new StringBuilder();
        cluster.setOyenteEventos(m -> eventos.append(m).append('\n'));
        Computador pc = cluster.obtenerPorIndice(0);
        Proceso largo = cpu("largo", 50, 1000);
        Proceso corto = cpu("corto", 5, 3);
        pc.recibirProceso(largo);
        pc.recibirProceso(corto);
        igual(20, pc.getRamUsada(), "los dos procesos ocupan RAM");
        for (int ciclo = 1; ciclo <= 3; ciclo++) {
            cluster.onTickGlobal(ciclo);
        }
        igual(EstadoProceso.TERMINADO, corto.getEstado(), "el proceso en espera termina al llegar a 0 su deadline");
        verificar(!corto.cumplioDeadline(), "no cumplio su deadline: no alcanzo a ejecutar sus instrucciones");
        igual(10, pc.getRamUsada(), "su memoria se libero");
        igual(1, pc.getColaTerminados().getTamanio(), "esta en la cola de terminados");
        verificar(eventos.toString().contains("llega a su deadline"), "el log registra el vencimiento");
        igual(EstadoProceso.EJECUCION, largo.getEstado(), "el otro proceso sigue ejecutando");
        igual(997, largo.getDeadline(), "el deadline de cada proceso baja un ciclo por ciclo de reloj");
    }

    private static void deadlineLiberaAUnBloqueadoEnSemaforo() {
        seccion("Un consumidor bloqueado que vence su deadline sale del semaforo");
        Cluster cluster = cluster(2, 100, 0);
        Computador pc = cluster.obtenerPorIndice(0);
        BufferSO b = cluster.crearBuffer("B1", 2, pc.getId());
        Proceso cons = comunicante("cons", b, false, 1, 2, 6);
        pc.recibirProceso(cons);
        for (int ciclo = 1; ciclo <= 3; ciclo++) {
            cluster.onTickGlobal(ciclo);
        }
        igual(EstadoProceso.BLOQUEADO, cons.getEstado(), "sin productor el consumidor queda bloqueado");
        igual(-1, b.getLlenos().getValor(), "esta contado en el semaforo llenos");
        for (int ciclo = 4; ciclo <= 6; ciclo++) {
            cluster.onTickGlobal(ciclo);
        }
        igual(EstadoProceso.TERMINADO, cons.getEstado(), "al llegar su deadline termina");
        igual(0, b.getLlenos().getValor(), "y deja de contar en el semaforo llenos");
        igual(0, b.getLlenos().getCantidadEnEspera(), "ya no esta en su cola");
        igual(0, pc.getColaBloqueados().getTamanio(), "ni en la cola de bloqueados");
        verificar(cons.getCiclosBloqueadoSemaforo() >= 2, "se midio el tiempo que paso bloqueado en el semaforo");
    }

    private static void esLiberaLaCpuParaOtros() {
        seccion("Mientras un proceso espera E/S, la CPU atiende a otro");
        Cluster cluster = cluster(2, 100, 0);
        Computador pc = cluster.obtenerPorIndice(0);
        Proceso io = new Proceso("io", TipoProceso.IO_BOUND, 1, 10, 1000, 4);
        io.setPrograma(new ProgramaIoBound(2, 3));
        Proceso calculo = cpu("calculo", 6, 1000);
        pc.recibirProceso(io);
        pc.recibirProceso(calculo);
        int fin = correrHasta(cluster, io, calculo);
        igual(10, fin, "los dos terminan en 10 ciclos (4 de E/S + 6 de calculo)");
        igual(10, pc.getCiclosOcupados(), "la CPU estuvo ocupada los 10 ciclos: no hubo ciclos perdidos en la E/S");
        verificar(calculo.getCicloFin() < io.getCicloFin(), "el calculo termino antes porque uso la CPU durante la E/S");
        Nodo<Proceso> t = pc.getColaTerminados().getLista().getCabeza();
        verificar(t != null, "ambos quedaron en terminados");
    }
}

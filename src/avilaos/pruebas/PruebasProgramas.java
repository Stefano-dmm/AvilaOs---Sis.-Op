package avilaos.pruebas;

import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.lanza;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.modelo.EstadoProceso;
import avilaos.modelo.ModoCPU;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.modelo.ResultadoCiclo;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.nucleo.politicas.FabricaPoliticas;
import avilaos.nucleo.programas.PasoComunicacion;
import avilaos.nucleo.programas.ProgramaCpuBound;
import avilaos.nucleo.programas.ProgramaIoBound;
import avilaos.nucleo.programas.ProgramaProductorConsumidor;
import avilaos.sincronizacion.BufferSO;

/** Prueba cada programa de proceso paso a paso, ejecutando directamente una instruccion por ciclo. */
public final class PruebasProgramas {

    private PruebasProgramas() {
    }

    private static Cluster clusterDePrueba(int latencia) {
        Computador.reiniciarContadorIds();
        Proceso.reiniciarContadorGlobal();
        Cluster cluster = new Cluster();
        for (int i = 1; i <= 2; i++) {
            Computador pc = new Computador("PC" + i, 200, 2);
            pc.setPolitica(FabricaPoliticas.crear(PoliticaPlanificacion.FCFS), PoliticaPlanificacion.FCFS);
            cluster.agregarComputador(pc);
        }
        cluster.setLatenciaRed(latencia);
        return cluster;
    }

    private static Proceso comunicante(String nombre, BufferSO buffer, boolean productor, int ciclos, int elementos) {
        Proceso p = new Proceso(nombre, productor ? TipoProceso.PRODUCTOR : TipoProceso.CONSUMIDOR, 1, 10, 1000,
                ProgramaProductorConsumidor.instruccionesTotales(ciclos, elementos));
        p.setPrograma(new ProgramaProductorConsumidor(productor, buffer, ciclos));
        return p;
    }

    private static void ponerEnCpu(Computador pc, Proceso p) {
        p.setComputadorId(pc.getId());
        p.setEstado(EstadoProceso.EJECUCION);
        pc.setProcesoEnCPU(p);
    }

    private static ResultadoCiclo paso(Computador pc, Proceso p) {
        return p.getPrograma().ejecutar(pc, p);
    }

    public static void ejecutar() {
        cpuBound();
        ioBound();
        productorLocal();
        consumidorBloqueadoYDespertado();
        accesoRemoto();
        terminacionAMitadDelProtocolo();
        validaciones();
    }

    private static void cpuBound() {
        seccion("Programa CPU bound");
        Cluster cluster = clusterDePrueba(0);
        Computador pc = cluster.obtenerPorIndice(0);
        Proceso p = new Proceso("cpu", TipoProceso.CPU_BOUND, 1, 10, 100, 3);
        p.setPrograma(new ProgramaCpuBound());
        ponerEnCpu(pc, p);
        igual(ResultadoCiclo.CONTINUA, paso(pc, p), "la primera instruccion continua");
        igual(ModoCPU.USUARIO, pc.getModoCPU(), "ejecuta en modo usuario");
        igual(ResultadoCiclo.CONTINUA, paso(pc, p), "la segunda instruccion continua");
        igual(ResultadoCiclo.TERMINADO, paso(pc, p), "la tercera instruccion termina el proceso");
    }

    private static void ioBound() {
        seccion("Programa I/O bound");
        Cluster cluster = clusterDePrueba(0);
        Computador pc = cluster.obtenerPorIndice(0);
        Proceso p = new Proceso("io", TipoProceso.IO_BOUND, 1, 10, 100, 10);
        p.setPrograma(new ProgramaIoBound(2, 3));
        ponerEnCpu(pc, p);
        igual(ResultadoCiclo.CONTINUA, paso(pc, p), "la primera rafaga de CPU continua");
        igual(ResultadoCiclo.BLOQUEADO, paso(pc, p), "tras la rafaga pide E/S y se bloquea");
        verificar(pc.getProcesoEnCPU() == null, "al bloquearse libera la CPU");
        igual(1, pc.getColaBloqueados().getTamanio(), "queda en la cola de bloqueados");
        igual(EstadoProceso.BLOQUEADO, p.getEstado(), "su estado es Bloqueado");
        verificar(p.getMotivoBloqueo().startsWith("E/S"), "el motivo de bloqueo es la E/S");
        for (int i = 1; i <= 3; i++) {
            pc.avanzarBloqueados();
            igual(1, pc.getColaBloqueados().getTamanio(), "sigue bloqueado tras " + i + " ciclo(s) de E/S");
        }
        pc.avanzarBloqueados();
        igual(0, pc.getColaBloqueados().getTamanio(), "al terminar la E/S sale de bloqueados");
        igual(1, pc.getColaListos().getTamanio(), "y pasa a Listos");
        igual(EstadoProceso.LISTO, p.getEstado(), "su estado vuelve a Listo");
        igual(null, p.getMotivoBloqueo(), "se borra el motivo de bloqueo");
    }

    private static void productorLocal() {
        seccion("Productor local");
        Cluster cluster = clusterDePrueba(0);
        Computador pc = cluster.obtenerPorIndice(0);
        BufferSO buffer = cluster.crearBuffer("B1", 2, pc.getId());
        Proceso p = comunicante("prod", buffer, true, 1, 2);
        ponerEnCpu(pc, p);
        igual(12, p.getTiempoRestante(), "dos elementos de trabajo 1 son 12 instrucciones");

        igual(ResultadoCiclo.CONTINUA, paso(pc, p), "paso 1: producir el elemento");
        igual(ModoCPU.USUARIO, pc.getModoCPU(), "producir se ejecuta en modo usuario");
        igual(ResultadoCiclo.CONTINUA, paso(pc, p), "paso 2: semWait(vacios)");
        igual(ModoCPU.SISTEMA_OPERATIVO, pc.getModoCPU(), "semWait se ejecuta en modo sistema operativo");
        igual(1, buffer.getVacios().getValor(), "semWait(vacios) toma un hueco");
        paso(pc, p);
        igual(0, buffer.getMutex().getValor(), "semWait(mutex) cierra la region critica");
        paso(pc, p);
        igual(1, buffer.getElementos(), "insertar deja un elemento en el buffer");
        paso(pc, p);
        igual(1, buffer.getMutex().getValor(), "semSignal(mutex) abre la region critica");
        paso(pc, p);
        igual(1, buffer.getLlenos().getValor(), "semSignal(llenos) anuncia el elemento");

        ResultadoCiclo ultimo = ResultadoCiclo.CONTINUA;
        for (int i = 0; i < 6; i++) {
            ultimo = paso(pc, p);
        }
        igual(ResultadoCiclo.TERMINADO, ultimo, "tras 12 instrucciones el productor termina");
        igual(2, buffer.getProducidos(), "produjo los 2 elementos pedidos");
        igual(0, buffer.getVacios().getValor(), "el buffer de 2 quedo sin huecos");
        igual(2, buffer.getLlenos().getValor(), "y con 2 elementos");
        igual(1, buffer.getMutex().getValor(), "el mutex quedo libre");
        igual(0, p.getTiempoRestante(), "no le quedan instrucciones");
    }

    private static void consumidorBloqueadoYDespertado() {
        seccion("Consumidor sobre un buffer vacio");
        Cluster cluster = clusterDePrueba(0);
        Computador pc = cluster.obtenerPorIndice(0);
        BufferSO buffer = cluster.crearBuffer("B1", 1, pc.getId());
        Proceso consumidor = comunicante("cons", buffer, false, 1, 1);
        Proceso productor = comunicante("prod", buffer, true, 1, 1);

        ponerEnCpu(pc, consumidor);
        igual(ResultadoCiclo.BLOQUEADO, paso(pc, consumidor), "semWait(llenos) sin elementos bloquea al consumidor");
        igual(-1, buffer.getLlenos().getValor(), "llenos queda en -1: hay un proceso esperando");
        igual(EstadoProceso.BLOQUEADO, consumidor.getEstado(), "el consumidor queda Bloqueado");
        verificar(consumidor.getMotivoBloqueo().contains("'llenos' del buffer B1"),
                "el motivo indica el semaforo y el buffer");
        igual(-1, consumidor.getBloqueoRestante(), "no es un bloqueo por tiempo");
        igual(1, pc.getColaBloqueados().getTamanio(), "esta en la cola de bloqueados");

        ponerEnCpu(pc, productor);
        for (int i = 0; i < 6; i++) {
            paso(pc, productor);
        }
        igual(1, buffer.getElementos(), "el productor dejo un elemento");
        verificar(consumidor.getSemaforoBloqueo() == null, "semSignal(llenos) saco al consumidor de la cola del semaforo");
        igual(EstadoProceso.BLOQUEADO, consumidor.getEstado(), "hasta el siguiente ciclo sigue marcado Bloqueado");
        pc.procesarDespertares();
        igual(EstadoProceso.LISTO, consumidor.getEstado(), "al procesar los despertares pasa a Listo");
        igual(1, pc.getColaListos().getTamanio(), "y entra a la cola de listos");
        igual(0, pc.getColaBloqueados().getTamanio(), "ya no esta en bloqueados");

        ponerEnCpu(pc, consumidor);
        ResultadoCiclo ultimo = ResultadoCiclo.CONTINUA;
        for (int i = 0; i < 5; i++) {
            ultimo = paso(pc, consumidor);
        }
        igual(ResultadoCiclo.TERMINADO, ultimo, "el consumidor completa su protocolo y termina");
        igual(1, buffer.getConsumidos(), "consumio el elemento");
        igual(0, buffer.getElementos(), "el buffer quedo vacio");
        igual(1, buffer.getVacios().getValor(), "el hueco volvio a estar libre");
    }

    private static void accesoRemoto() {
        seccion("Acceso remoto con latencia de red");
        Cluster cluster = clusterDePrueba(3);
        StringBuilder eventos = new StringBuilder();
        cluster.setOyenteEventos(mensaje -> eventos.append(mensaje).append('\n'));
        Computador anfitrion = cluster.obtenerPorIndice(0);
        Computador remoto = cluster.obtenerPorIndice(1);
        BufferSO buffer = cluster.crearBuffer("B1", 2, anfitrion.getId());

        Proceso p = comunicante("remoto", buffer, true, 1, 1);
        ponerEnCpu(remoto, p);
        paso(remoto, p);
        paso(remoto, p);
        paso(remoto, p);
        int restanteAntes = p.getTiempoRestante();
        igual(ResultadoCiclo.BLOQUEADO, paso(remoto, p), "el acceso a un buffer remoto bloquea al proceso");
        igual(0, buffer.getElementos(), "mientras espera a la red todavia no inserto");
        igual(restanteAntes, p.getTiempoRestante(), "esperar la red no consume instrucciones");
        igual(4, p.getBloqueoRestante(), "se bloquea por la latencia de 3 ciclos (mas el ciclo de arranque)");
        verificar(eventos.toString().contains("de forma remota"), "el log registra el acceso remoto");
        for (int i = 0; i < 4; i++) {
            remoto.avanzarBloqueados();
        }
        igual(EstadoProceso.LISTO, p.getEstado(), "tras la latencia vuelve a Listo");
        ponerEnCpu(remoto, p);
        igual(ResultadoCiclo.CONTINUA, paso(remoto, p), "ahora si inserta en el buffer");
        igual(1, buffer.getElementos(), "el elemento quedo en el buffer remoto");
        paso(remoto, p);
        igual(ResultadoCiclo.TERMINADO, paso(remoto, p), "el productor remoto termina");

        Proceso local = comunicante("local", buffer, true, 1, 1);
        ponerEnCpu(anfitrion, local);
        paso(anfitrion, local);
        paso(anfitrion, local);
        paso(anfitrion, local);
        igual(ResultadoCiclo.CONTINUA, paso(anfitrion, local), "en el anfitrion el acceso no se bloquea");
        igual(2, buffer.getElementos(), "y inserta de inmediato");
    }

    private static void terminacionAMitadDelProtocolo() {
        seccion("Un proceso que muere a mitad del protocolo no deja el buffer atascado");
        Cluster cluster = clusterDePrueba(0);
        Computador pc = cluster.obtenerPorIndice(0);
        BufferSO buffer = cluster.crearBuffer("B1", 3, pc.getId());

        Proceso a = comunicante("a", buffer, true, 1, 3);
        ponerEnCpu(pc, a);
        paso(pc, a);
        paso(pc, a);
        paso(pc, a);
        igual(PasoComunicacion.ACCESO_BUFFER, ((ProgramaProductorConsumidor) a.getPrograma()).getPaso(),
                "a esta por entrar a la region critica");
        igual(0, buffer.getMutex().getValor(), "y tiene el mutex");

        Proceso b = comunicante("b", buffer, true, 1, 3);
        ponerEnCpu(pc, b);
        paso(pc, b);
        paso(pc, b);
        igual(ResultadoCiclo.BLOQUEADO, paso(pc, b), "b se bloquea esperando el mutex");
        igual(1, buffer.getMutex().getCantidadEnEspera(), "b esta en la cola del mutex");

        b.getPrograma().alTerminar(pc, b);
        igual(0, buffer.getMutex().getCantidadEnEspera(), "si b muere sale de la cola del mutex");
        igual(2, buffer.getVacios().getValor(), "y devuelve el hueco que habia tomado (solo a retiene uno de 3)");

        a.getPrograma().alTerminar(pc, a);
        igual(1, buffer.getMutex().getValor(), "si a muere dentro de la region critica el mutex se libera");
        igual(3, buffer.getVacios().getValor(), "y los huecos vuelven al buffer");

        Proceso c = comunicante("c", buffer, true, 1, 3);
        ponerEnCpu(pc, c);
        for (int i = 0; i < 4; i++) {
            paso(pc, c);
        }
        igual(1, buffer.getElementos(), "c inserto un elemento");
        c.getPrograma().alTerminar(pc, c);
        igual(1, buffer.getMutex().getValor(), "si c muere con el elemento ya insertado libera el mutex");
        igual(1, buffer.getLlenos().getValor(), "y anuncia el elemento para que no se pierda");
    }

    private static void validaciones() {
        seccion("Validaciones de los programas");
        lanza(IllegalArgumentException.class, () -> new ProgramaIoBound(0, 1), "ciclos entre E/S 0 es invalido");
        lanza(IllegalArgumentException.class, () -> new ProgramaIoBound(1, 0), "duracion de E/S 0 es invalida");
        lanza(IllegalArgumentException.class, () -> new ProgramaProductorConsumidor(true, null, 1),
                "productor sin buffer es invalido");
        igual(32, ProgramaProductorConsumidor.instruccionesTotales(3, 4), "4 elementos de trabajo 3 son 32 instrucciones");
    }
}

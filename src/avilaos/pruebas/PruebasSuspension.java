package avilaos.pruebas;

import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.contratos.CriterioSuspension;
import avilaos.disco.PoliticaDisco;
import avilaos.estructuras.ListaEnlazada;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.MenorPrioridadMasBloqueado;
import avilaos.nucleo.SistemaAvilaOS;
import avilaos.nucleo.programas.ProgramaIoBound;

/**
 * El modelo de siete estados y el planificador a mediano plazo: cuando un proceso nuevo no cabe en la RAM se suspende a un
 * proceso Bloqueado (su memoria va al área de intercambio por la cola del disco), el evento de un suspendido lo pasa a
 * Listo/Suspendido, y cuando hay RAM libre vuelve a la memoria. También el criterio de selección y los casos borde.
 */
public final class PruebasSuspension {

    private PruebasSuspension() {
    }

    /** Proceso que ejecuta 2 instrucciones, se bloquea `espera` ciclos por una E/S y luego termina con su última instrucción. */
    private static Proceso bloqueante(String nombre, int prioridad, int memoria, int espera) {
        Proceso p = new Proceso(nombre, TipoProceso.IO_BOUND, prioridad, memoria, 5000, 3);
        p.setPrograma(new ProgramaIoBound(2, espera));
        return p;
    }

    private static Proceso corto(String nombre, int prioridad, int memoria, int instrucciones) {
        return new Proceso(nombre, TipoProceso.CPU_BOUND, prioridad, memoria, 5000, instrucciones);
    }

    public static void ejecutar() {
        ciclo();
        eventoDuranteLaSuspension();
        criterioDeSeleccion();
        sinEspacioEnElSwap();
        murePorDeadlineSuspendido();
    }

    private static void ciclo() {
        seccion("Suspensión: Bloqueado, Bloqueado/Suspendido, Listo/Suspendido y de vuelta a la RAM");
        SistemaAvilaOS s = PruebasProcesosES.sistema(32, 100, PoliticaDisco.FIFO);
        StringBuilder log = PruebasProcesosES.capturar(s);
        Proceso victima = bloqueante("victima", 1, 16, 200);
        Proceso vivo = corto("vivo", 5, 16, 40);
        Proceso nuevo = corto("nuevo", 3, 16, 3);
        s.iniciar(victima);
        s.iniciar(vivo);
        igual(0, s.getComputador().getRamLibre(), "la RAM de 32 queda llena con dos procesos de 16");
        int ciclo = PruebasProcesosES.correrHasta(s, 1, 10, () -> victima.getEstado() == EstadoProceso.BLOQUEADO);
        igual(EstadoProceso.BLOQUEADO, victima.getEstado(), "la víctima se bloquea esperando su E/S");
        s.iniciar(nuevo);
        igual(EstadoProceso.NUEVO, nuevo.getEstado(), "el proceso nuevo no cabe: espera en la cola a largo plazo");
        ciclo = PruebasProcesosES.correrHasta(s, ciclo + 1, 3, () -> victima.isSuspendiendo());
        verificar(victima.isSuspendiendo(), "el planificador a mediano plazo elige suspender a un proceso Bloqueado");
        igual(EstadoProceso.BLOQUEADO, victima.getEstado(), "mientras se escribe al swap sigue Bloqueado y en la RAM");
        igual(0, s.getComputador().getRamLibre(), "mientras se escribe al swap la RAM de la víctima sigue ocupada");
        igual(84, s.getDisco().getBloquesLibresSwap(), "su memoria reservó 16 bloques del área de intercambio");
        verificar(log.toString().contains("Se suspende al proceso P1-victima para admitir a P3-nuevo"), "el log explica la decisión");
        verificar(s.getDisco().getTamanioCola() + (s.getDisco().getEnServicio() == null ? 0 : 1) >= 1,
                "la suspensión es una solicitud en la cola de E/S del disco");
        ciclo = PruebasProcesosES.correrHasta(s, ciclo + 1, 200, () -> victima.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO);
        igual(EstadoProceso.BLOQUEADO_SUSPENDIDO, victima.getEstado(), "al terminar la escritura pasa a Bloqueado/Suspendido");
        igual(1, s.getComputador().getColaBloqueadosSuspendidos().getTamanio(), "y está en la cola de bloqueados/suspendidos");
        igual(0, s.getComputador().getColaBloqueados().getTamanio(), "ya no está en la de bloqueados");
        igual(1, s.getComputador().getSuspensiones(), "se cuenta una suspensión");
        verificar(nuevo.getEstado() != EstadoProceso.NUEVO, "su RAM liberada permite admitir al proceso nuevo");
        verificar(victima.getBloqueSwap() >= 100, "su memoria está en el área de intercambio, que empieza después del bloque 199");
        ciclo = PruebasProcesosES.correrHasta(s, ciclo + 1, 400, () -> victima.getEstado() == EstadoProceso.LISTO_SUSPENDIDO);
        igual(EstadoProceso.LISTO_SUSPENDIDO, victima.getEstado(), "cuando ocurre su evento pasa a Listo/Suspendido");
        igual(1, s.getComputador().getColaListosSuspendidos().getTamanio(), "y espera en la cola de listos/suspendidos");
        ciclo = PruebasProcesosES.correrHasta(s, ciclo + 1, 600, () -> victima.getEstado() == EstadoProceso.TERMINADO);
        igual(EstadoProceso.TERMINADO, victima.getEstado(), "al liberarse RAM vuelve, ejecuta su última instrucción y termina");
        igual(1, s.getComputador().getReactivaciones(), "se cuenta una reactivación");
        igual(100, s.getDisco().getBloquesLibresSwap(), "el swap quedó libre otra vez");
        igual(-1, victima.getBloqueSwap(), "el proceso ya no tiene bloques en el swap");
        verificar(log.toString().contains("Se reactiva al proceso P1-victima"), "el log registra la reactivación");
        verificar(log.toString().contains("P1-victima vuelve a la RAM"), "y el regreso a la RAM");
        verificar(victima.cumplioDeadline(), "terminó cumpliendo su deadline");
        igual(3, victima.getPc(), "ejecutó sus 3 instrucciones sin repetir ni perder ninguna");
    }

    private static void eventoDuranteLaSuspension() {
        seccion("Suspensión: el evento ocurre mientras se escribe al swap");
        SistemaAvilaOS s = PruebasProcesosES.sistema(32, 100, PoliticaDisco.FIFO);
        Proceso victima = bloqueante("corta", 1, 16, 4);
        Proceso vivo = corto("vivo", 5, 16, 400);
        Proceso nuevo = corto("nuevo", 3, 16, 3);
        s.iniciar(victima);
        s.iniciar(vivo);
        PruebasProcesosES.correrHasta(s, 1, 10, () -> victima.getEstado() == EstadoProceso.BLOQUEADO);
        s.iniciar(nuevo);
        PruebasProcesosES.correrHasta(s, 4, 400, () -> victima.getEstado() == EstadoProceso.LISTO_SUSPENDIDO);
        igual(EstadoProceso.LISTO_SUSPENDIDO, victima.getEstado(),
                "si su E/S termina durante la escritura al swap, la suspensión la deja directo en Listo/Suspendido");
        verificar(!victima.isEventoPendiente(), "y ya no queda el evento pendiente");
        igual(1, s.getComputador().getSuspensiones(), "cuenta como una suspensión");
        igual(0, s.getComputador().getColaBloqueadosSuspendidos().getTamanio(), "nunca pasó por Bloqueado/Suspendido");
    }

    private static void criterioDeSeleccion() {
        seccion("Suspensión: criterio de selección de la víctima");
        ListaEnlazada<Proceso> candidatos = new ListaEnlazada<>();
        Proceso alta = bloqueante("alta", 9, 16, 5);
        Proceso baja = bloqueante("baja", 2, 16, 5);
        Proceso bajaVieja = bloqueante("bajaVieja", 2, 16, 5);
        alta.setCicloBloqueo(10);
        baja.setCicloBloqueo(50);
        bajaVieja.setCicloBloqueo(20);
        candidatos.agregarAlFinal(alta);
        candidatos.agregarAlFinal(baja);
        candidatos.agregarAlFinal(bajaVieja);
        CriterioSuspension criterio = new MenorPrioridadMasBloqueado();
        igual(bajaVieja, criterio.elegir(candidatos, 60), "se suspende al de menor prioridad y, con empate, al que lleva más tiempo bloqueado");
        igual(null, criterio.elegir(new ListaEnlazada<>(), 60), "sin candidatos no elige a nadie");
        igual(baja, criterio.elegir(listaDe(alta, baja), 60), "entre prioridad 9 y 2 elige la de 2");
    }

    private static ListaEnlazada<Proceso> listaDe(Proceso... procesos) {
        ListaEnlazada<Proceso> lista = new ListaEnlazada<>();
        for (Proceso p : procesos) {
            lista.agregarAlFinal(p);
        }
        return lista;
    }

    private static void sinEspacioEnElSwap() {
        seccion("Suspensión: sin candidatos o sin espacio en el swap");
        SistemaAvilaOS sinSwap = PruebasProcesosES.sistema(32, 0, PoliticaDisco.FIFO);
        StringBuilder log = PruebasProcesosES.capturar(sinSwap);
        Proceso a = bloqueante("a", 1, 16, 300);
        Proceso b = corto("b", 5, 16, 300);
        Proceso c = corto("c", 3, 16, 3);
        sinSwap.iniciar(a);
        sinSwap.iniciar(b);
        PruebasProcesosES.correrHasta(sinSwap, 1, 10, () -> a.getEstado() == EstadoProceso.BLOQUEADO);
        sinSwap.iniciar(c);
        PruebasProcesosES.correrHasta(sinSwap, 4, 30, () -> false);
        igual(EstadoProceso.NUEVO, c.getEstado(), "sin espacio en el swap el proceso nuevo sigue esperando en Nuevos");
        igual(EstadoProceso.BLOQUEADO, a.getEstado(), "y nadie se suspende");
        verificar(log.toString().contains("No hay espacio en el área de intercambio"), "el log explica por qué no se suspendió");
        igual(0, sinSwap.getComputador().getSuspensiones(), "no se cuenta ninguna suspensión");

        SistemaAvilaOS sinBloqueados = PruebasProcesosES.sistema(32, 100, PoliticaDisco.FIFO);
        Proceso x = corto("x", 1, 16, 300);
        Proceso y = corto("y", 5, 16, 300);
        Proceso z = corto("z", 3, 16, 3);
        sinBloqueados.iniciar(x);
        sinBloqueados.iniciar(y);
        sinBloqueados.iniciar(z);
        PruebasProcesosES.correrHasta(sinBloqueados, 1, 30, () -> false);
        igual(EstadoProceso.NUEVO, z.getEstado(), "si no hay procesos Bloqueados que suspender, el nuevo espera hasta que se libere RAM");
        igual(0, sinBloqueados.getComputador().getSuspensiones(), "no se suspende a un proceso Listo");

        SistemaAvilaOS enorme = PruebasProcesosES.sistema(32, 100, PoliticaDisco.FIFO);
        Proceso w = bloqueante("w", 1, 16, 300);
        enorme.iniciar(w);
        enorme.iniciar(corto("gigante", 5, 64, 3));
        PruebasProcesosES.correrHasta(enorme, 1, 30, () -> false);
        verificar(!w.isSuspendiendo() && enorme.getDisco().getTamanioCola() == 0 && enorme.getDisco().getEnServicio() == null,
                "un proceso más grande que toda la RAM no provoca suspensiones inútiles: nunca cabría");
    }

    private static void murePorDeadlineSuspendido() {
        seccion("Suspensión: un proceso suspendido sigue teniendo deadline");
        SistemaAvilaOS s = PruebasProcesosES.sistema(32, 100, PoliticaDisco.FIFO);
        Proceso victima = new Proceso("mortal", TipoProceso.IO_BOUND, 1, 16, 120, 3);
        victima.setPrograma(new ProgramaIoBound(2, 1000));
        Proceso vivo = corto("vivo", 5, 16, 400);
        Proceso nuevo = corto("nuevo", 3, 16, 3);
        s.iniciar(victima);
        s.iniciar(vivo);
        PruebasProcesosES.correrHasta(s, 1, 10, () -> victima.getEstado() == EstadoProceso.BLOQUEADO);
        s.iniciar(nuevo);
        int ciclo = PruebasProcesosES.correrHasta(s, 4, 200, () -> victima.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO);
        igual(EstadoProceso.BLOQUEADO_SUSPENDIDO, victima.getEstado(), "queda suspendido esperando una E/S larga");
        PruebasProcesosES.correrHasta(s, ciclo + 1, 200, () -> victima.getEstado() == EstadoProceso.TERMINADO);
        igual(EstadoProceso.TERMINADO, victima.getEstado(), "su deadline lo termina aunque esté en el disco");
        igual(100, s.getDisco().getBloquesLibresSwap(), "al terminar libera sus bloques del swap");
        igual(0, s.getComputador().getColaBloqueadosSuspendidos().getTamanio(), "y sale de la cola de bloqueados/suspendidos");
        verificar(!victima.cumplioDeadline(), "no cumplió su deadline");
    }
}

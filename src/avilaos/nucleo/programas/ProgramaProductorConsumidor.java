package avilaos.nucleo.programas;

import avilaos.contratos.ProgramaProceso;
import avilaos.modelo.ModoCPU;
import avilaos.modelo.Proceso;
import avilaos.modelo.ResultadoCiclo;
import avilaos.nucleo.Computador;
import avilaos.sincronizacion.BufferSO;
import avilaos.sincronizacion.SemaforoSO;

/**
 * Productor o consumidor de un buffer acotado, con el algoritmo clasico de semaforos:
 *
 * <pre>
 * productor:  producir; semWait(vacios); semWait(mutex); insertar; semSignal(mutex); semSignal(llenos)
 * consumidor: semWait(llenos); semWait(mutex); extraer; semSignal(mutex); semSignal(vacios); consumir
 * </pre>
 *
 * El orden de los semWait importa: tomar mutex antes que vacios/llenos puede causar interbloqueo.
 * Cada semWait, semSignal, insercion o extraccion es una instruccion en modo sistema operativo.
 * Si el buffer esta en otro computador, antes de cada acceso el proceso se bloquea tantos ciclos
 * como la latencia de red.
 */
public class ProgramaProductorConsumidor implements ProgramaProceso {

    /** Instrucciones que ejecuta por elemento, sin contar las esperas: trabajo mas 5 operaciones. */
    public static int instruccionesTotales(int ciclosPorElemento, int elementos) {
        return elementos * (ciclosPorElemento + 5);
    }

    private final boolean productor;
    private final BufferSO buffer;
    private final int ciclosPorElemento;
    private PasoComunicacion paso;
    private int trabajoHecho;
    private boolean accesoRemotoPagado;

    public ProgramaProductorConsumidor(boolean productor, BufferSO buffer, int ciclosPorElemento) {
        if (buffer == null) {
            throw new IllegalArgumentException("El proceso necesita un buffer.");
        }
        if (ciclosPorElemento < 1) {
            throw new IllegalArgumentException("Los ciclos por elemento deben ser al menos 1.");
        }
        this.productor = productor;
        this.buffer = buffer;
        this.ciclosPorElemento = ciclosPorElemento;
        this.paso = productor ? PasoComunicacion.TRABAJAR : PasoComunicacion.WAIT_PRIMERO;
    }

    public PasoComunicacion getPaso() {
        return paso;
    }

    public BufferSO getBuffer() {
        return buffer;
    }

    private SemaforoSO primero() {
        return productor ? buffer.getVacios() : buffer.getLlenos();
    }

    private SemaforoSO segundo() {
        return productor ? buffer.getLlenos() : buffer.getVacios();
    }

    private static String motivo(SemaforoSO semaforo, BufferSO buffer) {
        return "semaforo '" + semaforo.getNombre() + "' del buffer " + buffer.getNombre();
    }

    /** Ejecuta una instruccion del sistema operativo: consume el ciclo y la CPU pasa a modo SO. */
    private static void instruccionSO(Computador computador, Proceso proceso) {
        computador.setModoCPU(ModoCPU.SISTEMA_OPERATIVO);
        proceso.ejecutarUnCiclo();
    }

    @Override
    public ResultadoCiclo ejecutar(Computador computador, Proceso proceso) {
        switch (paso) {
            case TRABAJAR -> {
                computador.setModoCPU(ModoCPU.USUARIO);
                proceso.ejecutarUnCiclo();
                trabajoHecho++;
                if (trabajoHecho >= ciclosPorElemento) {
                    trabajoHecho = 0;
                    paso = PasoComunicacion.WAIT_PRIMERO;
                }
            }
            case WAIT_PRIMERO -> {
                instruccionSO(computador, proceso);
                paso = PasoComunicacion.WAIT_MUTEX;
                if (!primero().semWait(proceso)) {
                    computador.bloquearPorSemaforo(proceso, motivo(primero(), buffer));
                    return ResultadoCiclo.BLOQUEADO;
                }
            }
            case WAIT_MUTEX -> {
                instruccionSO(computador, proceso);
                paso = PasoComunicacion.ACCESO_BUFFER;
                if (!buffer.getMutex().semWait(proceso)) {
                    computador.bloquearPorSemaforo(proceso, motivo(buffer.getMutex(), buffer));
                    return ResultadoCiclo.BLOQUEADO;
                }
            }
            case ACCESO_BUFFER -> {
                int latencia = latenciaDeAcceso(computador);
                if (latencia > 0 && !accesoRemotoPagado) {
                    accesoRemotoPagado = true;
                    computador.setModoCPU(ModoCPU.SISTEMA_OPERATIVO);
                    computador.registrarEvento(proceso.getEtiqueta() + " accede al buffer "
                            + buffer.getNombre() + " de forma remota (" + latencia + " ciclos de red)");
                    computador.bloquearPorTiempo(proceso, latencia,
                            "acceso remoto al buffer " + buffer.getNombre());
                    return ResultadoCiclo.BLOQUEADO;
                }
                instruccionSO(computador, proceso);
                accesoRemotoPagado = false;
                if (productor) {
                    buffer.insertar();
                    computador.registrarEvento(proceso.getEtiqueta() + " produce un elemento en " + buffer);
                } else {
                    buffer.extraer();
                    computador.registrarEvento(proceso.getEtiqueta() + " consume un elemento de " + buffer);
                }
                paso = PasoComunicacion.SIGNAL_MUTEX;
            }
            case SIGNAL_MUTEX -> {
                instruccionSO(computador, proceso);
                paso = PasoComunicacion.SIGNAL_SEGUNDO;
                computador.liberarSemaforo(buffer.getMutex());
            }
            case SIGNAL_SEGUNDO -> {
                instruccionSO(computador, proceso);
                paso = PasoComunicacion.TRABAJAR;
                computador.liberarSemaforo(segundo());
            }
        }
        return proceso.estaTerminado() ? ResultadoCiclo.TERMINADO : ResultadoCiclo.CONTINUA;
    }

    /** Ciclos de red que cuesta acceder al buffer desde este computador (0 si es el anfitrion). */
    private int latenciaDeAcceso(Computador computador) {
        if (computador.getId() == buffer.getAnfitrion().getId() || computador.getCluster() == null) {
            return 0;
        }
        return computador.getCluster().getLatenciaRed();
    }

    /**
     * Si el proceso muere (por deadline) a mitad del protocolo devuelve lo que tenia tomado, para no
     * dejar el mutex cerrado ni perder huecos o elementos del buffer.
     */
    @Override
    public void alTerminar(Computador computador, Proceso proceso) {
        SemaforoSO bloqueadoEn = proceso.getSemaforoBloqueo();
        if (bloqueadoEn != null) {
            bloqueadoEn.cancelarEspera(proceso);
        }
        switch (paso) {
            case WAIT_MUTEX -> {
                if (bloqueadoEn != primero()) {
                    computador.liberarSemaforo(primero());
                }
            }
            case ACCESO_BUFFER -> {
                if (bloqueadoEn != buffer.getMutex()) {
                    computador.liberarSemaforo(buffer.getMutex());
                }
                computador.liberarSemaforo(primero());
            }
            case SIGNAL_MUTEX -> {
                computador.liberarSemaforo(buffer.getMutex());
                computador.liberarSemaforo(segundo());
            }
            case SIGNAL_SEGUNDO -> computador.liberarSemaforo(segundo());
            default -> {
            }
        }
    }
}

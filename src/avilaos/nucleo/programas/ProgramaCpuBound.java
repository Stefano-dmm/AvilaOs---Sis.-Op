package avilaos.nucleo.programas;

import avilaos.contratos.ProgramaProceso;
import avilaos.modelo.ModoCPU;
import avilaos.modelo.Proceso;
import avilaos.modelo.ResultadoCiclo;
import avilaos.nucleo.Computador;

/** Proceso que solo usa la CPU: cada ciclo ejecuta una instruccion de usuario hasta terminar. */
public class ProgramaCpuBound implements ProgramaProceso {

    @Override
    public ResultadoCiclo ejecutar(Computador computador, Proceso proceso) {
        computador.setModoCPU(ModoCPU.USUARIO);
        proceso.ejecutarUnCiclo();
        return proceso.estaTerminado() ? ResultadoCiclo.TERMINADO : ResultadoCiclo.CONTINUA;
    }
}

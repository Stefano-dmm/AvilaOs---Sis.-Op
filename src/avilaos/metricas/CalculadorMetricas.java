package avilaos.metricas;

import avilaos.estructuras.Cola;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.sincronizacion.BufferSO;

/** Calcula las metricas de rendimiento a partir del estado actual de los computadores. */
public final class CalculadorMetricas {

    private CalculadorMetricas() {
    }

    public static ResumenMetricas deComputador(Computador pc) {
        Acumulador acum = new Acumulador();
        acum.sumar(pc);
        return acum.resumir("PC" + pc.getId() + " " + pc.getNombre(), pc.getCiclosTotales());
    }

    public static ResumenMetricas global(Cluster cluster) {
        Acumulador acum = new Acumulador();
        int ciclos = 0;
        Nodo<Computador> actual = cluster.getComputadores().getCabeza();
        while (actual != null) {
            acum.sumar(actual.getDato());
            ciclos = Math.max(ciclos, actual.getDato().getCiclosTotales());
            actual = actual.getSiguiente();
        }
        return acum.resumir("Sistema", ciclos);
    }

    /** Elementos producidos y consumidos de cada buffer, en el orden en que se crearon. */
    public static ResumenBuffer[] deBuffers(Cluster cluster) {
        ResumenBuffer[] resumenes = new ResumenBuffer[cluster.getBuffers().getTamanio()];
        int i = 0;
        Nodo<BufferSO> actual = cluster.getBuffers().getCabeza();
        while (actual != null) {
            BufferSO b = actual.getDato();
            resumenes[i++] = new ResumenBuffer(b.getNombre(), b.getAnfitrion().getNombre(),
                    b.getProducidos(), b.getConsumidos(), b.getElementos(), b.getCapacidad());
            actual = actual.getSiguiente();
        }
        return resumenes;
    }

    private static final class Acumulador {

        private long sumaBloqueoSemaforo;
        private int comunicantes;
        private long ciclosOcupados;
        private long ciclosTotales;
        private int completados;
        private int terminados;
        private int cumplidos;
        private long sumaRespuesta;
        private int conRespuesta;
        private double sumaFraccion;
        private double sumaFraccionCuadrado;
        private int conFraccion;

        void sumar(Computador pc) {
            ciclosOcupados += pc.getCiclosOcupados();
            ciclosTotales += pc.getCiclosTotales();
            int ahora = pc.getCicloActual();
            if (pc.getProcesoEnCPU() != null) {
                sumarProceso(pc.getProcesoEnCPU(), ahora);
            }
            sumarCola(pc.getColaNuevos(), ahora);
            sumarCola(pc.getColaListos(), ahora);
            sumarCola(pc.getColaBloqueados(), ahora);
            sumarCola(pc.getColaTerminados(), ahora);
        }

        private void sumarCola(Cola<Proceso> cola, int ahora) {
            ListaEnlazada<Proceso> lista = cola.getLista();
            Nodo<Proceso> actual = lista.getCabeza();
            while (actual != null) {
                sumarProceso(actual.getDato(), ahora);
                actual = actual.getSiguiente();
            }
        }

        private void sumarProceso(Proceso p, int ahora) {
            if (p.getTipo() == TipoProceso.PRODUCTOR || p.getTipo() == TipoProceso.CONSUMIDOR) {
                sumaBloqueoSemaforo += p.getCiclosBloqueadoSemaforo();
                comunicantes++;
            }
            if (p.getEstado() == EstadoProceso.TERMINADO) {
                terminados++;
                if (p.cumplioDeadline()) {
                    completados++;
                    cumplidos++;
                }
            }
            if (p.getCicloPrimerDespacho() >= 0 && p.getCicloLlegada() >= 0) {
                sumaRespuesta += p.getCicloPrimerDespacho() - p.getCicloLlegada();
                conRespuesta++;
            }
            if (p.getCicloLlegada() >= 0) {
                int hasta = p.getCicloFin() >= 0 ? p.getCicloFin() : ahora;
                int enSistema = hasta - p.getCicloLlegada();
                if (enSistema > 0) {
                    double fraccion = Math.min(1.0, (double) p.getCiclosEjecutados() / enSistema);
                    sumaFraccion += fraccion;
                    sumaFraccionCuadrado += fraccion * fraccion;
                    conFraccion++;
                }
            }
        }

        ResumenMetricas resumir(String nombre, int ciclosReloj) {
            double throughput = ciclosReloj > 0 ? (double) completados / ciclosReloj : Double.NaN;
            double utilizacion = ciclosTotales > 0 ? (double) ciclosOcupados / ciclosTotales : Double.NaN;
            double respuesta = conRespuesta > 0 ? (double) sumaRespuesta / conRespuesta : Double.NaN;
            double cumplimiento = terminados > 0 ? (double) cumplidos / terminados : Double.NaN;
            double equidad = (conFraccion > 0 && sumaFraccionCuadrado > 0)
                    ? (sumaFraccion * sumaFraccion) / (conFraccion * sumaFraccionCuadrado)
                    : Double.NaN;
            double bloqueo = comunicantes > 0 ? (double) sumaBloqueoSemaforo / comunicantes : Double.NaN;
            return new ResumenMetricas(nombre, throughput, utilizacion, respuesta, cumplimiento,
                    equidad, bloqueo, completados, terminados);
        }
    }
}

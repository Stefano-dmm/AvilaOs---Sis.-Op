package avilaos.pruebas;

import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.lanza;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.modelo.PoliticaPlanificacion;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.nucleo.politicas.FabricaPoliticas;
import avilaos.sincronizacion.BufferSO;

public final class PruebasBuffer {

    private PruebasBuffer() {
    }

    private static Cluster clusterBasico() {
        Computador.reiniciarContadorIds();
        Cluster cluster = new Cluster();
        for (int i = 1; i <= 2; i++) {
            Computador pc = new Computador("PC" + i, 100, 2);
            pc.setPolitica(FabricaPoliticas.crear(PoliticaPlanificacion.FCFS), PoliticaPlanificacion.FCFS);
            cluster.agregarComputador(pc);
        }
        return cluster;
    }

    public static void ejecutar() {
        seccion("Buffers acotados");

        Cluster cluster = clusterBasico();
        Computador pc1 = cluster.obtenerPorIndice(0);
        BufferSO b = cluster.crearBuffer("B1", 5, pc1.getId());
        igual(5 * BufferSO.TAMANO_ELEMENTO, pc1.getRamUsada(), "el buffer ocupa RAM en su anfitrion");
        igual(0, cluster.obtenerPorIndice(1).getRamUsada(), "el otro computador no pierde RAM");
        igual(5, b.getVacios().getValor(), "el semaforo vacios arranca con la capacidad");
        igual(0, b.getLlenos().getValor(), "el semaforo llenos arranca en 0");
        igual(1, b.getMutex().getValor(), "el mutex arranca en 1");

        b.insertar();
        b.insertar();
        b.extraer();
        igual(1, b.getElementos(), "insertar dos y extraer uno deja un elemento");
        igual(2, b.getProducidos(), "cuenta los elementos producidos");
        igual(1, b.getConsumidos(), "cuenta los elementos consumidos");

        lanza(IllegalStateException.class, () -> {
            b.extraer();
            b.extraer();
        }, "extraer de un buffer vacio es un error del protocolo");
        BufferSO pequeno = cluster.crearBuffer("B2", 1, pc1.getId());
        pequeno.insertar();
        lanza(IllegalStateException.class, pequeno::insertar, "insertar en un buffer lleno es un error del protocolo");

        lanza(IllegalStateException.class, () -> cluster.crearBuffer("Enorme", 100, pc1.getId()),
                "un buffer que no cabe en la RAM del anfitrion se rechaza");
        lanza(IllegalArgumentException.class, () -> cluster.crearBuffer("", 3, pc1.getId()),
                "buffer sin nombre es invalido");
        lanza(IllegalArgumentException.class, () -> cluster.crearBuffer("a,b", 3, pc1.getId()),
                "el nombre del buffer no puede llevar comas");
        lanza(IllegalArgumentException.class, () -> cluster.crearBuffer("B3", 0, pc1.getId()),
                "capacidad 0 es invalida");
        lanza(IllegalArgumentException.class, () -> cluster.crearBuffer("B3", 3, 99),
                "anfitrion inexistente es invalido");
        igual(2, cluster.getBuffers().getTamanio(), "los buffers rechazados no se registran");

        cluster.setLatenciaRed(4);
        igual(4, cluster.getLatenciaRed(), "la latencia de red se puede configurar");
        lanza(IllegalArgumentException.class, () -> cluster.setLatenciaRed(-1), "latencia negativa es invalida");

        verificar(b.getAnfitrion() == pc1, "el buffer recuerda su computador anfitrion");
    }
}

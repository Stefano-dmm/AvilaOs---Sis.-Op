package avilaos.pruebas;

import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.lanza;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.config.ArchivoConfiguracion;
import avilaos.config.ConfigBuffer;
import avilaos.config.ConfigComputador;
import avilaos.config.ConfigProceso;
import avilaos.config.Configuracion;
import avilaos.config.ConstructorCluster;
import avilaos.estructuras.Nodo;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Cluster;
import avilaos.nucleo.Computador;
import avilaos.sincronizacion.BufferSO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pruebas de la configuracion: guardar, cargar, validar y construir un cluster desde un archivo. */
public final class PruebasConfiguracion {

    private PruebasConfiguracion() {
    }

    private static Configuracion configCompleta() {
        Configuracion c = new Configuracion();
        c.setCicloMs(200);
        c.setLatenciaRed(3);
        c.agregarComputador(new ConfigComputador("Nodo-A", 300, PoliticaPlanificacion.FCFS, 2));
        c.agregarComputador(new ConfigComputador("Nodo-B", 400, PoliticaPlanificacion.ROUND_ROBIN, 4));
        c.agregarBuffer(new ConfigBuffer("B1", 3, 0));
        c.agregarBuffer(new ConfigBuffer("B2", 2, 1));
        c.agregarProceso(new ConfigProceso("calc", TipoProceso.CPU_BOUND, 2, 20, 500, 10, 0));
        c.agregarProceso(new ConfigProceso("io", TipoProceso.IO_BOUND, 1, 20, 500, 8, 1, -1, 2, 0, 3));
        c.agregarProceso(new ConfigProceso("prod", TipoProceso.PRODUCTOR, 1, 20, 900, 0, 1, 0, 1, 4, 0));
        c.agregarProceso(new ConfigProceso("cons", TipoProceso.CONSUMIDOR, 1, 20, 900, 0, 0, 0, 1, 4, 0));
        c.agregarProceso(new ConfigProceso("auto", TipoProceso.CPU_BOUND, 3, 20, 500, 5, -1));
        return c;
    }

    private static String escribir(String contenido) throws IOException {
        Path ruta = Files.createTempFile("avilaos", ".csv");
        Files.writeString(ruta, contenido, StandardCharsets.UTF_8);
        return ruta.toString();
    }

    private static String mensajeDeCarga(String contenido) throws IOException {
        Path ruta = Path.of(escribir(contenido));
        try {
            ArchivoConfiguracion.cargar(ruta);
            return "";
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        } finally {
            Files.deleteIfExists(ruta);
        }
    }

    public static void ejecutar() throws IOException {
        seccion("Configuracion: guardar y cargar con buffers");
        Configuracion original = configCompleta();
        Path ruta = Files.createTempFile("avilaos", ".csv");
        ArchivoConfiguracion.guardar(ruta, original);
        Configuracion leida = ArchivoConfiguracion.cargar(ruta);
        Files.deleteIfExists(ruta);

        igual(200, leida.getCicloMs(), "se conserva la duracion del ciclo");
        igual(3, leida.getLatenciaRed(), "se conserva la latencia de red");
        igual(2, leida.getComputadores().getTamanio(), "se conservan los computadores");
        igual(2, leida.getBuffers().getTamanio(), "se conservan los buffers");
        igual(5, leida.getProcesos().getTamanio(), "se conservan los procesos");
        igual(original.getBuffers().obtener(1), leida.getBuffers().obtener(1), "un buffer vuelve identico");
        for (int i = 0; i < 5; i++) {
            igual(original.getProcesos().obtener(i), leida.getProcesos().obtener(i),
                    "el proceso " + i + " vuelve con todos sus atributos");
        }

        seccion("Configuracion: formato anterior y validaciones");
        String viejo = "COMPUTADOR,A,300,FCFS,2\nCOMPUTADOR,B,300,FCFS,2\nPROCESO,p,CPU_BOUND,1,10,100,5,0\n";
        Path rutaVieja = Path.of(escribir(viejo));
        Configuracion cargadaVieja = ArchivoConfiguracion.cargar(rutaVieja);
        Files.deleteIfExists(rutaVieja);
        igual(new ConfigProceso("p", TipoProceso.CPU_BOUND, 1, 10, 100, 5, 0), cargadaVieja.getProcesos().obtener(0),
                "una linea PROCESO de 8 campos se carga como CPU bound");

        String base = "COMPUTADOR,A,100,FCFS,2\nCOMPUTADOR,B,100,FCFS,2\n";
        verificar(mensajeDeCarga(base + "BUFFER,B1,3,5\n").contains("Linea 3"), "un buffer en un computador inexistente se rechaza con su linea");
        verificar(mensajeDeCarga(base + "BUFFER,B1,30,0\n").contains("no caben"), "buffers que no caben en la RAM se rechazan");
        verificar(mensajeDeCarga(base + "PROCESO,p,PRODUCTOR,1,10,100,0,0,0,1,3,0\n").contains("buffer que no existe"),
                "un productor sin buffer valido se rechaza");
        verificar(mensajeDeCarga(base + "BUFFER,B1,3,0\nPROCESO,p,PRODUCTOR,1,10,100,0,0,0,0,3,0\n").contains("Ciclos por elemento"),
                "un productor con 0 ciclos por elemento se rechaza");
        verificar(mensajeDeCarga(base + "BUFFER,B1,3,0\nPROCESO,p,CONSUMIDOR,1,10,100,0,0,0,1,0,0\n").contains("Elementos"),
                "un consumidor con 0 elementos se rechaza");
        verificar(mensajeDeCarga(base + "PROCESO,p,IO_BOUND,1,10,100,5,0,-1,2,0,0\n").contains("Ciclos de E/S"),
                "un proceso de E/S con 0 ciclos de E/S se rechaza");
        verificar(mensajeDeCarga(base + "PROCESO,p,CPU_BOUND,1,500,100,5,0\n").contains("Memoria"),
                "un proceso con mas memoria que la RAM de su computador se rechaza");
        verificar(mensajeDeCarga(base + "PROCESO,p,CPU_BOUND,1,150,100,5,-1\n").contains("Memoria"),
                "un proceso automatico mas grande que cualquier RAM se rechaza");
        verificar(mensajeDeCarga(base + "PROCESO,p,CPU_BOUND,1,10,100\n").contains("campos"),
                "una linea PROCESO incompleta se rechaza");
        verificar(mensajeDeCarga(base + "PROCESO,p,GRAFICO,1,10,100,5,0\n").contains("Linea 3"),
                "un tipo de proceso desconocido se rechaza indicando la linea");
        verificar(mensajeDeCarga(base + "BUFFER,B1,tres,0\n").contains("numero entero"), "una capacidad no numerica se rechaza");
        verificar(mensajeDeCarga("COMPUTADOR,A,100,FCFS,2\n").contains("al menos"), "con un solo computador la configuracion es invalida");
        lanza(IllegalArgumentException.class, () -> configCompleta().agregarBuffer(new ConfigBuffer("x,y", 1, 0)),
                "el nombre de un buffer no puede llevar comas");

        construirDesdeArchivo();
    }

    private static void construirDesdeArchivo() throws IOException {
        seccion("Configuracion: construir un cluster y simularlo desde el archivo");
        Path ruta = Files.createTempFile("avilaos", ".csv");
        ArchivoConfiguracion.guardar(ruta, configCompleta());
        Cluster cluster = ConstructorCluster.construir(ArchivoConfiguracion.cargar(ruta));
        Files.deleteIfExists(ruta);

        igual(3, cluster.getLatenciaRed(), "el cluster usa la latencia de la configuracion");
        igual(2, cluster.getBuffers().getTamanio(), "se crean los buffers");
        BufferSO b1 = cluster.obtenerBuffer(0);
        igual("Nodo-A", b1.getAnfitrion().getNombre(), "B1 vive en el primer computador");
        igual(3 * BufferSO.TAMANO_ELEMENTO, cluster.obtenerPorIndice(0).getRamUsada() - procesosEn(cluster.obtenerPorIndice(0)),
                "el buffer ocupa RAM en su anfitrion");

        Computador a = cluster.obtenerPorIndice(0);
        Computador b = cluster.obtenerPorIndice(1);
        igual(1, contar(b, "io"), "el proceso de E/S esta en el segundo computador");
        igual(1, contar(a, "calc"), "el proceso de calculo esta en el primer computador");
        verificar(a.getCargaActual() + b.getCargaActual() == 5, "los 5 procesos quedaron repartidos entre los dos computadores");
        verificar(contar(a, "auto") + contar(b, "auto") == 1, "el proceso automatico fue asignado por AvilaOS a alguno");

        Proceso[] todos = procesos(cluster);
        boolean terminaron = false;
        for (int ciclo = 1; ciclo <= 2000 && !terminaron; ciclo++) {
            cluster.onTickGlobal(ciclo);
            terminaron = true;
            for (Proceso p : todos) {
                if (p.getEstado() != EstadoProceso.TERMINADO) {
                    terminaron = false;
                }
            }
        }
        verificar(terminaron, "la simulacion cargada desde el archivo termina con los 5 procesos");
        igual(4, b1.getProducidos(), "el productor produjo sus 4 elementos");
        igual(4, b1.getConsumidos(), "el consumidor consumio sus 4 elementos");
        igual(1, b1.getMutex().getValor(), "el mutex quedo libre");
    }

    private static Proceso[] procesos(Cluster cluster) {
        Proceso[] lista = new Proceso[5];
        int i = 0;
        Nodo<Computador> pc = cluster.getComputadores().getCabeza();
        while (pc != null) {
            i = copiar(pc.getDato(), lista, i);
            pc = pc.getSiguiente();
        }
        return lista;
    }

    private static int copiar(Computador pc, Proceso[] destino, int desde) {
        int i = desde;
        if (pc.getProcesoEnCPU() != null) {
            destino[i++] = pc.getProcesoEnCPU();
        }
        Nodo<Proceso> n = pc.getColaListos().getLista().getCabeza();
        while (n != null) {
            destino[i++] = n.getDato();
            n = n.getSiguiente();
        }
        n = pc.getColaNuevos().getLista().getCabeza();
        while (n != null) {
            destino[i++] = n.getDato();
            n = n.getSiguiente();
        }
        n = pc.getColaBloqueados().getLista().getCabeza();
        while (n != null) {
            destino[i++] = n.getDato();
            n = n.getSiguiente();
        }
        return i;
    }

    private static int procesosEn(Computador pc) {
        Proceso[] tmp = new Proceso[10];
        int n = copiar(pc, tmp, 0);
        int memoria = 0;
        for (int i = 0; i < n; i++) {
            memoria += tmp[i].getMemoriaRequerida();
        }
        return memoria;
    }

    private static int contar(Computador pc, String nombre) {
        Proceso[] tmp = new Proceso[10];
        int n = copiar(pc, tmp, 0);
        int total = 0;
        for (int i = 0; i < n; i++) {
            if (tmp[i].getNombre().equals(nombre)) {
                total++;
            }
        }
        return total;
    }
}

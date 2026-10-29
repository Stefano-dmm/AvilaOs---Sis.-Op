package avilaos.pruebas;

import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.lanza;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.config.ConfigBuffer;
import avilaos.config.ConfigComputador;
import avilaos.config.ConfigProceso;
import avilaos.config.Configuracion;
import avilaos.config.ConstructorCluster;
import avilaos.config.FabricaProcesos;
import avilaos.config.LectorFormulario;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;
import avilaos.nucleo.Cluster;
import avilaos.sincronizacion.BufferSO;

/** Validaciones de tipo y rango de los formularios (Vista 1 y buffers), sin abrir ventanas. */
public final class PruebasFormularios {

    private PruebasFormularios() {
    }

    private static String error(Runnable bloque) {
        try {
            bloque.run();
            return "";
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }

    private static ConfigProceso cpu(String nombre, String prioridad, String memoria, String deadline, String instrucciones) {
        return LectorFormulario.proceso(nombre, TipoProceso.CPU_BOUND, prioridad, memoria, deadline,
                instrucciones, "", "", "", -1, -1);
    }

    public static void ejecutar() {
        seccion("Formularios: validacion de tipo y rango");

        igual(7, LectorFormulario.entero(" 7 ", "X", 1, 10), "los espacios alrededor de un numero se ignoran");
        verificar(error(() -> LectorFormulario.entero("abc", "La memoria", 1, 10)).contains("numero entero"),
                "letras en un campo numerico se rechazan");
        verificar(error(() -> LectorFormulario.entero("3.5", "La memoria", 1, 10)).contains("numero entero"),
                "un decimal en un campo entero se rechaza");
        verificar(error(() -> LectorFormulario.entero("", "La memoria", 1, 10)).contains("vacio"),
                "un campo vacio se rechaza");
        verificar(error(() -> LectorFormulario.entero("99999999999", "La memoria", 1, 10)).contains("numero entero"),
                "un numero demasiado grande se rechaza sin romper el programa");
        verificar(error(() -> LectorFormulario.entero("0", "La memoria", 1, 10)).contains("entre 1 y 10"),
                "un valor fuera de rango dice cual es el rango");
        verificar(error(() -> LectorFormulario.entero("-4", "La prioridad", 1, 100)).contains("entre 1 y 100"),
                "un valor negativo se rechaza");

        ConfigProceso cpuOk = cpu("calc", "2", "64", "100", "30");
        igual(30, cpuOk.instrucciones(), "un proceso CPU bound toma sus instrucciones");
        igual(-1, cpuOk.indiceComputador(), "indice -1 significa asignacion automatica");
        verificar(error(() -> cpu("", "1", "1", "1", "1")).contains("nombre"), "un nombre vacio se rechaza");
        verificar(error(() -> cpu("a,b", "1", "1", "1", "1")).contains("comas"), "un nombre con comas se rechaza");
        verificar(error(() -> cpu("x", "1", "1", "1", "cien")).contains("instrucciones"),
                "instrucciones no numericas se rechazan nombrando el campo");
        verificar(error(() -> cpu("x", "0", "1", "1", "1")).contains("prioridad"), "prioridad 0 se rechaza");
        verificar(error(() -> cpu("x", "1", "1", "0", "1")).contains("deadline"), "deadline 0 se rechaza");
        verificar(error(() -> LectorFormulario.proceso("x", null, "1", "1", "1", "1", "", "", "", -1, -1)).contains("tipo"),
                "un proceso sin tipo se rechaza");

        ConfigProceso es = LectorFormulario.proceso("io", TipoProceso.IO_BOUND, "1", "10", "50", "8", "2", "", "3", 0, -1);
        igual(2, es.intervalo(), "un proceso de E/S toma los ciclos entre E/S");
        igual(3, es.ciclosES(), "y la duracion de cada E/S");
        verificar(error(() -> LectorFormulario.proceso("io", TipoProceso.IO_BOUND, "1", "10", "50", "8", "2", "", "", 0, -1))
                .contains("E/S"), "a un proceso de E/S le falta la duracion");

        ConfigProceso prod = LectorFormulario.proceso("p", TipoProceso.PRODUCTOR, "1", "10", "900", "", "2", "5", "", 0, 0);
        igual(35, prod.instrucciones(), "un productor calcula sus instrucciones: 5 elementos x (2 + 5)");
        igual(5, prod.elementos(), "y conserva cuantos elementos debe producir");
        verificar(error(() -> LectorFormulario.proceso("p", TipoProceso.PRODUCTOR, "1", "10", "900", "", "2", "5", "", 0, -1))
                .contains("buffer"), "un productor sin buffer elegido se rechaza");
        verificar(error(() -> LectorFormulario.proceso("p", TipoProceso.CONSUMIDOR, "1", "10", "900", "", "0", "5", "", 0, 0))
                .contains("ciclos por elemento"), "un consumidor con 0 ciclos por elemento se rechaza");
        verificar(error(() -> LectorFormulario.proceso("p", TipoProceso.CONSUMIDOR, "1", "10", "900", "", "1", "-2", "", 0, 0))
                .contains("elementos"), "un consumidor con elementos negativos se rechaza");

        ConfigBuffer buffer = LectorFormulario.buffer("B1", "4", 1);
        igual(4, buffer.capacidad(), "un buffer toma su capacidad");
        verificar(error(() -> LectorFormulario.buffer("B1", "0", 0)).contains("capacidad"), "capacidad 0 se rechaza");
        verificar(error(() -> LectorFormulario.buffer(" ", "3", 0)).contains("nombre"), "un buffer sin nombre se rechaza");
        verificar(error(() -> LectorFormulario.buffer("B1", "3", -1)).contains("anfitrion"), "un buffer sin anfitrion se rechaza");

        seccion("Formularios: del texto a un proceso en ejecucion");
        Configuracion config = new Configuracion();
        config.agregarComputador(new ConfigComputador("A", 100, PoliticaPlanificacion.FCFS, 2));
        config.agregarComputador(new ConfigComputador("B", 200, PoliticaPlanificacion.FCFS, 2));
        config.agregarBuffer(buffer);
        verificar(error(() -> config.agregarBuffer(new ConfigBuffer("b1", 2, 0))).contains("Ya existe"),
                "no se aceptan dos buffers con el mismo nombre (sin importar mayusculas)");
        verificar(error(() -> config.agregarProceso(cpu("grande", "1", "500", "50", "5"))).contains("entre 1 y 200"),
                "un proceso automatico mas grande que toda la RAM se rechaza");
        verificar(error(() -> config.agregarProceso(LectorFormulario.proceso("pb", TipoProceso.CPU_BOUND, "1", "150", "50", "5", "", "", "", 0, -1)))
                .contains("entre 1 y 100"), "la memoria se compara con la RAM del computador elegido");
        verificar(error(() -> config.agregarProceso(LectorFormulario.proceso("pc", TipoProceso.PRODUCTOR, "1", "10", "50", "", "1", "2", "", 0, 3)))
                .contains("buffer que no existe"), "un buffer inexistente se rechaza");

        Cluster cluster = ConstructorCluster.construir(config);
        BufferSO b = cluster.obtenerBuffer(0);
        Proceso productor = FabricaProcesos.crear(LectorFormulario.proceso("prod", TipoProceso.PRODUCTOR, "1", "10",
                "900", "", "1", "2", "", 0, 0), cluster);
        Proceso consumidor = FabricaProcesos.crear(LectorFormulario.proceso("cons", TipoProceso.CONSUMIDOR, "1", "10",
                "900", "", "1", "2", "", 0, 0), cluster);
        verificar(cluster.asignarProcesoAutomatico(productor) != null, "AvilaOS asigna el productor");
        verificar(cluster.asignarProcesoAutomatico(consumidor) != null, "AvilaOS asigna el consumidor");
        boolean terminaron = false;
        for (int ciclo = 1; ciclo <= 1000 && !terminaron; ciclo++) {
            cluster.onTickGlobal(ciclo);
            terminaron = productor.getEstado() == EstadoProceso.TERMINADO && consumidor.getEstado() == EstadoProceso.TERMINADO;
        }
        verificar(terminaron, "el productor y el consumidor creados desde texto terminan");
        igual(2, b.getProducidos(), "el productor produjo 2 elementos");
        igual(2, b.getConsumidos(), "el consumidor consumio 2 elementos");
        lanza(IllegalArgumentException.class, () -> cluster.crearBuffer("B1", 2, 1), "el cluster tambien rechaza nombres de buffer repetidos");
    }
}

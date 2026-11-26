package avilaos.config;

import avilaos.archivos.Permiso;
import avilaos.archivos.TipoNodo;
import avilaos.disco.OperacionES;
import avilaos.disco.PoliticaDisco;
import avilaos.estructuras.ListaEnlazada;
import avilaos.json.Json;
import avilaos.json.JsonArreglo;
import avilaos.json.JsonObjeto;
import avilaos.modelo.PoliticaPlanificacion;

/**
 * Lo que dice el JSON de procesos y configuración del Proyecto 2: la configuración global (duración del ciclo, tamaño del
 * swap, política inicial del disco), el computador (RAM, política de CPU y quantum) y la lista de procesos de E/S. Se lee y
 * valida completo antes de crear nada, para que un dato inválido no deje el sistema a medias.
 */
public record DefinicionProcesos(int cicloMs, int bloquesSwap, PoliticaDisco politicaDisco, int velocidadCabezal,
                                 int ram, PoliticaPlanificacion politica, int quantum,
                                 ListaEnlazada<EntradaProceso> procesos) {

    /** Posiciones por ciclo del cabezal cuando el JSON no lo dice. */
    public static final int VELOCIDAD_POR_DEFECTO = 5;
    /** Prioridad de un proceso cuando el JSON no la dice. */
    public static final int PRIORIDAD_POR_DEFECTO = 5;

    /** Un proceso de E/S tal como lo describe el JSON; los campos opcionales tienen valores por defecto. */
    public record EntradaProceso(String nombre, int memoria, int deadline, int instrucciones, String ruta,
                                 OperacionES operacion, int prioridad, String usuario, TipoNodo tipoNuevo,
                                 int bloquesNuevo, Permiso permisoNuevo, String nuevoNombre) {
    }

    /** Lee y valida el JSON; cualquier dato inválido lanza IllegalArgumentException con un mensaje que nombra el campo y el proceso. */
    public static DefinicionProcesos desdeJson(String texto) {
        JsonObjeto raiz = Json.leerObjeto(texto);
        JsonObjeto global = raiz.objeto("globalConfig");
        int cicloMs = global.entero("cycleDurationMs");
        int swap = global.entero("swapSizeBlocks");
        PoliticaDisco politicaDisco = politicaDeDisco(global.texto("initialDiskPolicy"));
        int velocidad = global.entero("diskHeadSpeed", VELOCIDAD_POR_DEFECTO);
        if (cicloMs < Configuracion.MIN_CICLO_MS || cicloMs > Configuracion.MAX_CICLO_MS) {
            throw new IllegalArgumentException("cycleDurationMs debe estar entre " + Configuracion.MIN_CICLO_MS + " y " + Configuracion.MAX_CICLO_MS + ".");
        }
        if (swap < 0 || swap > 1000) {
            throw new IllegalArgumentException("swapSizeBlocks debe estar entre 0 y 1000.");
        }
        if (velocidad < 1 || velocidad > 1000) {
            throw new IllegalArgumentException("diskHeadSpeed debe estar entre 1 y 1000.");
        }
        JsonObjeto computador = raiz.objeto("computer");
        int ram = computador.entero("ramCapacity");
        if (ram < Configuracion.MIN_RAM || ram > Configuracion.MAX_RAM) {
            throw new IllegalArgumentException("ramCapacity debe estar entre " + Configuracion.MIN_RAM + " y " + Configuracion.MAX_RAM + ".");
        }
        PoliticaPlanificacion politica = politicaDeCpu(computador.texto("policy"));
        int quantum = computador.entero("quantum", 0);
        if (quantum < 0 || quantum > Configuracion.MAX_QUANTUM) {
            throw new IllegalArgumentException("quantum debe estar entre 0 y " + Configuracion.MAX_QUANTUM + ".");
        }
        if (politica == PoliticaPlanificacion.ROUND_ROBIN && quantum < Configuracion.MIN_QUANTUM) {
            throw new IllegalArgumentException("Con Round Robin el quantum debe ser al menos " + Configuracion.MIN_QUANTUM + ".");
        }
        ListaEnlazada<EntradaProceso> procesos = new ListaEnlazada<>();
        JsonArreglo lista = raiz.arreglo("processes");
        for (int i = 0; i < lista.tamanio(); i++) {
            procesos.agregarAlFinal(entrada(lista.objeto(i), i + 1, ram));
        }
        return new DefinicionProcesos(cicloMs, swap, politicaDisco, velocidad, ram, politica,
                Math.max(quantum, Configuracion.MIN_QUANTUM), procesos);
    }

    private static EntradaProceso entrada(JsonObjeto p, int posicion, int ram) {
        String nombre = p.texto("name");
        String donde = "El proceso " + posicion + " (" + nombre + "): ";
        try {
            int memoria = p.entero("memory");
            if (memoria < 1 || memoria > ram) {
                throw new IllegalArgumentException("memory debe estar entre 1 y " + ram + " (la RAM).");
            }
            int deadline = p.entero("deadline");
            if (deadline < 1 || deadline > 1_000_000) {
                throw new IllegalArgumentException("deadline debe estar entre 1 y 1000000.");
            }
            int instrucciones = p.entero("instructions");
            if (instrucciones < 1 || instrucciones > Configuracion.MAX_INSTRUCCIONES) {
                throw new IllegalArgumentException("instructions debe estar entre 1 y " + Configuracion.MAX_INSTRUCCIONES + ".");
            }
            int prioridad = p.entero("priority", PRIORIDAD_POR_DEFECTO);
            if (prioridad < 1 || prioridad > 100) {
                throw new IllegalArgumentException("priority debe estar entre 1 y 100.");
            }
            OperacionES operacion = operacion(p.texto("operation"));
            TipoNodo tipo = tipoNuevo(p.texto("nodeType", "FILE"));
            int bloques = p.entero("blocks", 1);
            if (operacion == OperacionES.CREAR && tipo == TipoNodo.ARCHIVO && bloques < 1) {
                throw new IllegalArgumentException("blocks debe ser al menos 1 para crear un archivo.");
            }
            Permiso permiso = "public".equalsIgnoreCase(p.texto("permissions", "private")) ? Permiso.PUBLICO : Permiso.PRIVADO;
            return new EntradaProceso(nombre, memoria, deadline, instrucciones, p.texto("targetPath"), operacion,
                    prioridad, p.texto("user", "admin"), tipo, bloques, permiso, p.texto("newName", null));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(donde + e.getMessage(), e);
        }
    }

    private static OperacionES operacion(String texto) {
        return switch (texto.trim().toUpperCase()) {
            case "CREATE", "CREAR" -> OperacionES.CREAR;
            case "READ", "LEER" -> OperacionES.LEER;
            case "UPDATE", "ACTUALIZAR" -> OperacionES.ACTUALIZAR;
            case "DELETE", "ELIMINAR" -> OperacionES.ELIMINAR;
            default -> throw new IllegalArgumentException("operation debe ser CREATE, READ, UPDATE o DELETE, no \"" + texto + "\".");
        };
    }

    private static TipoNodo tipoNuevo(String texto) {
        return switch (texto.trim().toUpperCase()) {
            case "FILE", "ARCHIVO" -> TipoNodo.ARCHIVO;
            case "DIRECTORY", "DIRECTORIO" -> TipoNodo.DIRECTORIO;
            default -> throw new IllegalArgumentException("nodeType debe ser FILE o DIRECTORY, no \"" + texto + "\".");
        };
    }

    private static PoliticaDisco politicaDeDisco(String texto) {
        return switch (texto.trim().toUpperCase().replace("-", "_")) {
            case "FIFO" -> PoliticaDisco.FIFO;
            case "SSTF" -> PoliticaDisco.SSTF;
            case "SCAN" -> PoliticaDisco.SCAN;
            case "C_SCAN", "CSCAN" -> PoliticaDisco.C_SCAN;
            default -> throw new IllegalArgumentException("initialDiskPolicy debe ser FIFO, SSTF, SCAN o C-SCAN, no \"" + texto + "\".");
        };
    }

    private static PoliticaPlanificacion politicaDeCpu(String texto) {
        return switch (texto.trim().toUpperCase().replace("-", "_")) {
            case "FCFS" -> PoliticaPlanificacion.FCFS;
            case "EDF" -> PoliticaPlanificacion.EDF;
            case "ROUND_ROBIN", "RR" -> PoliticaPlanificacion.ROUND_ROBIN;
            case "PRIORIDADES", "PRIORITY", "PRIORIDAD" -> PoliticaPlanificacion.PRIORIDADES;
            default -> throw new IllegalArgumentException("policy debe ser FCFS, EDF, ROUND_ROBIN o PRIORIDADES, no \"" + texto + "\".");
        };
    }
}

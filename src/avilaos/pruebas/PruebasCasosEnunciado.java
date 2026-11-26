package avilaos.pruebas;

import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.lanza;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.archivos.DefinicionSistemaArchivos;
import avilaos.config.DefinicionProcesos;
import avilaos.disco.PoliticaDisco;
import avilaos.disco.SolicitudES;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.estructuras.Nodo;
import avilaos.nucleo.SistemaAvilaOS;

/**
 * Los dos JSON del enunciado cargados en el sistema completo: los 8 procesos del enunciado hacen sus solicitudes y se
 * comprueba que, con la cola llena y el cabezal en 50, cada política atiende en el orden de los casos P1 a P4.
 */
public final class PruebasCasosEnunciado {

    private PruebasCasosEnunciado() {
    }

    private static final String PROCESOS = """
            { "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 100, "initialDiskPolicy": "FIFO" },
              "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
              "processes": [
                { "name": "Proc_95", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/sistema/config.sys", "operation": "READ" },
                { "name": "Proc_180", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/usuarios/video.mp4", "operation": "READ" },
                { "name": "Proc_34", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/usuarios/notas.txt", "operation": "UPDATE" },
                { "name": "Proc_119", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/usuarios/img1.png", "operation": "DELETE" },
                { "name": "Proc_11", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/sistema/boot.bin", "operation": "READ" },
                { "name": "Proc_123", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/usuarios/data.csv", "operation": "UPDATE" },
                { "name": "Proc_62", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/sistema/script.py", "operation": "READ" },
                { "name": "Proc_64", "memory": 16, "deadline": 500, "instructions": 2, "targetPath": "/sistema/core.dll", "operation": "READ" }
              ] }
            """;

    public static void ejecutar() {
        cargaDeLosJson();
        casosPorPolitica();
        jsonInvalidos();
    }

    private static SistemaAvilaOS cargar() {
        DefinicionSistemaArchivos archivos = DefinicionSistemaArchivos.desdeJson(PruebasArchivos.JSON_ENUNCIADO);
        DefinicionProcesos procesos = DefinicionProcesos.desdeJson(PROCESOS);
        SistemaAvilaOS s = SistemaAvilaOS.construir(archivos, procesos);
        s.lanzarProcesos(procesos);
        return s;
    }

    private static void cargaDeLosJson() {
        seccion("Casos del enunciado: carga de los dos JSON");
        DefinicionProcesos d = DefinicionProcesos.desdeJson(PROCESOS);
        igual(300, d.cicloMs(), "lee la duración del ciclo");
        igual(100, d.bloquesSwap(), "lee el tamaño del área de intercambio");
        igual(PoliticaDisco.FIFO, d.politicaDisco(), "lee la política inicial del disco");
        igual(1024, d.ram(), "lee la RAM del computador");
        igual(PoliticaPlanificacion.FCFS, d.politica(), "lee la política de CPU");
        igual(1, d.quantum(), "un quantum 0 con FCFS se acepta y queda en el mínimo");
        igual(8, d.procesos().getTamanio(), "lee los 8 procesos");
        SistemaAvilaOS s = cargar();
        igual(8, s.getComputador().getColaListos().getTamanio(), "los 8 procesos entran a Listos (hay RAM para todos)");
        igual(1024 - 8 * 16, s.getComputador().getRamLibre(), "cada uno ocupa 16 unidades de RAM");
        igual(200, s.getDisco().getBloquesArchivos(), "el disco tiene los 200 bloques del JSON");
        igual(100, s.getDisco().getBloquesSwap(), "y el área de intercambio de 100");
        igual(50, s.getDisco().getPosicion(), "con el cabezal en el bloque 50");
        Proceso primero = s.getComputador().getColaListos().frente();
        igual("/sistema/config.sys", primero.getRutaArchivo(), "el PCB de cada proceso guarda su ruta");
    }

    /** Bloques de las solicitudes atendidas, en el orden en que el disco las atendió, leídos del log. */
    private static String ordenAtendido(StringBuilder log) {
        StringBuilder orden = new StringBuilder();
        String texto = log.toString();
        int pos = 0;
        while ((pos = texto.indexOf("en el bloque ", pos)) >= 0) {
            int inicio = pos + "en el bloque ".length();
            int fin = texto.indexOf(' ', inicio);
            orden.append(orden.length() == 0 ? "" : ",").append(texto, inicio, fin);
            pos = fin;
        }
        return orden.toString();
    }

    private static String atender(PoliticaDisco politica) {
        SistemaAvilaOS s = cargar();
        StringBuilder log = PruebasProcesosES.capturar(s);
        s.getDisco().setRetenido(true);
        int ciclo = PruebasProcesosES.correrHasta(s, 1, 60, () -> s.getDisco().getTamanioCola() == 8);
        igual(8, s.getDisco().getTamanioCola(), "con el disco retenido las 8 solicitudes llegan a la cola (política " + politica + ")");
        SolicitudES[] cola = s.getDisco().copiarCola();
        StringBuilder llegada = new StringBuilder();
        for (SolicitudES r : cola) {
            llegada.append(llegada.length() == 0 ? "" : ",").append(r.getBloque());
        }
        igual("95,180,34,119,11,123,62,64", llegada.toString(), "las solicitudes están en el orden de llegada del enunciado");
        s.getDisco().setPolitica(politica);
        s.getDisco().setRetenido(false);
        PruebasProcesosES.correrHasta(s, ciclo + 1, 4000, () -> contarTerminados(s) == 8);
        igual(8, s.getDisco().getAtendidas(), "el disco atiende las 8 solicitudes con " + politica);
        igual(8, contarTerminados(s), "y los 8 procesos terminan");
        return ordenAtendido(log);
    }

    private static int contarTerminados(SistemaAvilaOS s) {
        int cuenta = 0;
        Nodo<Proceso> actual = s.getComputador().getColaTerminados().getLista().getCabeza();
        while (actual != null) {
            if (actual.getDato().getEstado() == EstadoProceso.TERMINADO) {
                cuenta++;
            }
            actual = actual.getSiguiente();
        }
        return cuenta;
    }

    private static void casosPorPolitica() {
        seccion("Casos del enunciado: orden de atención con los 8 procesos (cabezal en 50)");
        igual("95,180,34,119,11,123,62,64", atender(PoliticaDisco.FIFO), "P1 FIFO en el sistema completo");
        igual("62,64,34,11,95,119,123,180", atender(PoliticaDisco.SSTF), "P2 SSTF en el sistema completo");
        igual("62,64,95,119,123,180,34,11", atender(PoliticaDisco.SCAN), "P3 SCAN en el sistema completo");
        igual("62,64,95,119,123,180,11,34", atender(PoliticaDisco.C_SCAN), "P4 C-SCAN en el sistema completo");
        SistemaAvilaOS s = cargar();
        s.getDisco().setRetenido(true);
        s.getDisco().setRetenido(false);
        verificar(!s.getDisco().isRetenido(), "soltar el disco vuelve a atender solicitudes");
    }

    private static void jsonInvalidos() {
        seccion("Casos del enunciado: JSON de procesos inválido");
        String base = """
                { "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 100, "initialDiskPolicy": "%s" },
                  "computer": { "ramCapacity": %d, "policy": "%s", "quantum": %d },
                  "processes": [ %s ] }
                """;
        String proceso = "{ \"name\": \"P\", \"memory\": %d, \"deadline\": 100, \"instructions\": 2, \"targetPath\": \"/mbr.bin\", \"operation\": \"%s\" }";
        lanza(IllegalArgumentException.class, () -> DefinicionProcesos.desdeJson(base.formatted("RARA", 1024, "FCFS", 0, "")),
                "una política de disco que no existe");
        lanza(IllegalArgumentException.class, () -> DefinicionProcesos.desdeJson(base.formatted("FIFO", 1024, "LOTE", 0, "")),
                "una política de CPU que no existe");
        lanza(IllegalArgumentException.class, () -> DefinicionProcesos.desdeJson(base.formatted("FIFO", 0, "FCFS", 0, "")),
                "una RAM de 0");
        lanza(IllegalArgumentException.class, () -> DefinicionProcesos.desdeJson(base.formatted("FIFO", 1024, "ROUND_ROBIN", 0, "")),
                "Round Robin con quantum 0");
        lanza(IllegalArgumentException.class, () -> DefinicionProcesos.desdeJson(base.formatted("FIFO", 64, "FCFS", 0, proceso.formatted(100, "READ"))),
                "un proceso que pide más memoria que la RAM");
        lanza(IllegalArgumentException.class, () -> DefinicionProcesos.desdeJson(base.formatted("FIFO", 64, "FCFS", 0, proceso.formatted(16, "BORRAR_TODO"))),
                "una operación desconocida");
        igual(1, DefinicionProcesos.desdeJson(base.formatted("c-scan", 64, "fcfs", 0, proceso.formatted(16, "delete"))).procesos().getTamanio(),
                "los nombres de política y operación no distinguen mayúsculas ni el guion de C-SCAN");
        lanza(IllegalArgumentException.class, () -> DefinicionProcesos.desdeJson("{\"computer\": {}}"), "falta globalConfig");
        DefinicionProcesos conLeer = DefinicionProcesos.desdeJson(base.formatted("FIFO", 64, "FCFS", 0, proceso.formatted(16, "READ")));
        SistemaAvilaOS s = SistemaAvilaOS.construir(DefinicionSistemaArchivos.desdeJson(PruebasArchivos.JSON_ENUNCIADO), conLeer);
        igual(1, s.lanzarProcesos(conLeer), "una carga válida entrega el proceso");
        DefinicionProcesos malo = DefinicionProcesos.desdeJson(base.formatted("FIFO", 64, "FCFS", 0,
                proceso.formatted(16, "READ") + "," + proceso.formatted(16, "READ").replace("/mbr.bin", "sin_barra")));
        SistemaAvilaOS t = SistemaAvilaOS.construir(DefinicionSistemaArchivos.desdeJson(PruebasArchivos.JSON_ENUNCIADO), malo);
        lanza(IllegalArgumentException.class, () -> t.lanzarProcesos(malo), "una ruta inválida en el segundo proceso rechaza la carga");
        igual(0, t.getComputador().getCargaActual(), "y no deja entregado el primero: la carga es todo o nada");
    }
}

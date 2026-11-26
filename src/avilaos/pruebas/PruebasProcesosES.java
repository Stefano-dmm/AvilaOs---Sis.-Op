package avilaos.pruebas;

import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.lanza;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.archivos.DefinicionSistemaArchivos;
import avilaos.archivos.Permiso;
import avilaos.archivos.TipoNodo;
import avilaos.disco.OperacionES;
import avilaos.disco.PoliticaDisco;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.ModoCPU;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;
import avilaos.nucleo.SistemaAvilaOS;
import java.util.function.BooleanSupplier;

/**
 * Los procesos de E/S sobre archivos: pasan por las colas del computador, piden al disco en modo sistema operativo,
 * quedan Bloqueados por E/S hasta que el disco los atiende y entonces aplican la operación (crear, leer, actualizar,
 * eliminar). También los rechazos de permisos, la muerte por deadline y el cambio de política de disco.
 */
public final class PruebasProcesosES {

    private PruebasProcesosES() {
    }

    /** Sistema con el sistema de archivos del enunciado, 1024 de RAM, FCFS y el disco en FIFO. */
    static SistemaAvilaOS sistema(int ram, int swap, PoliticaDisco politicaDisco) {
        DefinicionSistemaArchivos definicion = DefinicionSistemaArchivos.desdeJson(PruebasArchivos.JSON_ENUNCIADO);
        return SistemaAvilaOS.construir(definicion, ram, 3, PoliticaPlanificacion.FCFS, swap, politicaDisco, 5);
    }

    static StringBuilder capturar(SistemaAvilaOS s) {
        StringBuilder log = new StringBuilder();
        s.getCluster().setOyenteEventos(m -> log.append(m).append('\n'));
        return log;
    }

    /** Avanza ciclos desde `desde` hasta que se cumpla la condición o pasen `maximo`; devuelve el último ciclo corrido. */
    static int correrHasta(SistemaAvilaOS s, int desde, int maximo, BooleanSupplier condicion) {
        int ciclo = desde;
        while (ciclo < desde + maximo) {
            s.avanzar(ciclo);
            if (condicion.getAsBoolean()) {
                return ciclo;
            }
            ciclo++;
        }
        return ciclo;
    }

    static Proceso leer(SistemaAvilaOS s, String nombre, String ruta, int instrucciones, int deadline) {
        return s.crearProcesoArchivo(nombre, 5, 16, deadline, instrucciones, OperacionES.LEER, ruta, "admin",
                TipoNodo.ARCHIVO, 1, Permiso.PRIVADO, null);
    }

    public static void ejecutar() {
        leerUnArchivo();
        crearYEliminar();
        actualizar();
        rechazos();
        muereEsperando();
        politicaEnVivo();
        validaciones();
    }

    private static void leerUnArchivo() {
        seccion("Proceso de E/S: leer un archivo de punta a punta");
        SistemaAvilaOS s = sistema(1024, 100, PoliticaDisco.FIFO);
        StringBuilder log = capturar(s);
        Proceso p = leer(s, "lector", "/sistema/config.sys", 2, 500);
        s.iniciar(p);
        igual(EstadoProceso.LISTO, p.getEstado(), "con RAM suficiente el proceso nuevo pasa a Listos");
        igual(OperacionES.LEER, p.getOperacionES(), "el PCB guarda la operación pedida");
        igual("/sistema/config.sys", p.getRutaArchivo(), "y la ruta del archivo");
        s.avanzar(1);
        igual(ModoCPU.USUARIO, s.getComputador().getModoCPU(), "su primera instrucción es de usuario");
        s.avanzar(2);
        igual(EstadoProceso.BLOQUEADO, p.getEstado(), "en su última instrucción pide la E/S y queda Bloqueado");
        igual(ModoCPU.SISTEMA_OPERATIVO, s.getComputador().getModoCPU(), "la solicitud al disco es una instrucción en modo sistema operativo");
        verificar(p.getMotivoBloqueo().contains("LEER /sistema/config.sys"), "el motivo del bloqueo dice la operación y el archivo");
        igual(null, s.getComputador().getProcesoEnCPU(), "la CPU queda libre mientras el disco trabaja");
        igual(1, s.getComputador().getColaBloqueados().getTamanio(), "el proceso está en la cola de bloqueados");
        int fin = correrHasta(s, 3, 200, () -> p.getEstado() == EstadoProceso.TERMINADO);
        igual(EstadoProceso.TERMINADO, p.getEstado(), "cuando el disco atiende la solicitud el proceso despierta y termina");
        igual(2, p.getPc(), "ejecutó sus 2 instrucciones");
        verificar(p.cumplioDeadline(), "terminó antes de su deadline");
        verificar(fin > 30, "leer 24 bloques con el cabezal a 45 posiciones tarda más de 30 ciclos (hubo " + fin + ")");
        verificar(log.toString().contains("El disco atiende la solicitud de P1-lector en el bloque 95 (FIFO)"),
                "el log registra la decisión del disco");
        verificar(log.toString().contains("P1-lector pide al disco LEER /sistema/config.sys (bloque 95)"), "y la solicitud del proceso");
        igual(1, s.getDisco().getAtendidas(), "el disco atendió una solicitud");
        igual(45, s.getDisco().getDesplazamientoTotal(), "el cabezal se movió de 50 a 95");
        igual(95, s.getDisco().getPosicion(), "y quedó en el bloque 95");
    }

    private static void crearYEliminar() {
        seccion("Proceso de E/S: crear y eliminar archivos");
        SistemaAvilaOS s = sistema(1024, 100, PoliticaDisco.SSTF);
        s.getArchivos().eliminar("/usuarios/img1.png");
        igual(4, s.getDisco().getBloquesLibresArchivos(), "se liberaron 4 bloques para poder crear");
        Proceso crea = s.crearProcesoArchivo("crea", 5, 16, 500, 2, OperacionES.CREAR, "/usuarios/user1/nuevo.bin", "user1",
                TipoNodo.ARCHIVO, 3, Permiso.PRIVADO, null);
        s.getArchivos().crear("/usuarios/user1", TipoNodo.DIRECTORIO, 0, "user1", Permiso.PRIVADO, "carga");
        s.iniciar(crea);
        correrHasta(s, 1, 4, () -> crea.getEstado() == EstadoProceso.BLOQUEADO);
        igual(1, s.getDisco().getBloquesLibresArchivos(), "al generar la solicitud ya quedan reservados los 3 bloques");
        igual(null, s.getArchivos().buscar("/usuarios/user1/nuevo.bin"), "pero el archivo todavía no está en el árbol");
        correrHasta(s, 5, 200, () -> crea.getEstado() == EstadoProceso.TERMINADO);
        verificar(s.getArchivos().buscar("/usuarios/user1/nuevo.bin") != null, "al atender el disco la solicitud el archivo aparece");
        igual("P1-crea", s.getArchivos().buscar("/usuarios/user1/nuevo.bin").getCreador(), "y guarda el proceso que lo creó");
        igual("user1", s.getArchivos().buscar("/usuarios/user1/nuevo.bin").getDueno(), "su dueño es quien pidió crearlo");

        Proceso borra = s.crearProcesoArchivo("borra", 5, 16, 500, 2, OperacionES.ELIMINAR, "/usuarios/data.csv", "admin",
                TipoNodo.ARCHIVO, 1, Permiso.PRIVADO, null);
        s.iniciar(borra);
        int libresAntes = s.getDisco().getBloquesLibresArchivos();
        correrHasta(s, 300, 300, () -> borra.getEstado() == EstadoProceso.TERMINADO);
        igual(null, s.getArchivos().buscar("/usuarios/data.csv"), "eliminar quita el archivo del árbol");
        igual(libresAntes + 57, s.getDisco().getBloquesLibresArchivos(), "y libera sus 57 bloques");

        Proceso dir = s.crearProcesoArchivo("mkdir", 5, 16, 500, 1, OperacionES.CREAR, "/usuarios/docs", "admin",
                TipoNodo.DIRECTORIO, 0, Permiso.PUBLICO, null);
        s.iniciar(dir);
        correrHasta(s, 700, 100, () -> dir.getEstado() == EstadoProceso.TERMINADO);
        verificar(s.getArchivos().buscar("/usuarios/docs").esDirectorio(), "crear un directorio también pasa por el disco");

        Proceso sinEspacio = s.crearProcesoArchivo("grande", 5, 16, 500, 1, OperacionES.CREAR, "/usuarios/grande.bin", "admin",
                TipoNodo.ARCHIVO, 150, Permiso.PRIVADO, null);
        s.iniciar(sinEspacio);
        correrHasta(s, 800, 5, () -> sinEspacio.getEstado() == EstadoProceso.TERMINADO);
        igual(EstadoProceso.TERMINADO, sinEspacio.getEstado(), "si no hay bloques suficientes el proceso termina sin llegar a bloquearse");
        igual(null, s.getArchivos().buscar("/usuarios/grande.bin"), "y el archivo no se crea");
    }

    private static void actualizar() {
        seccion("Proceso de E/S: actualizar (reescribir y renombrar)");
        SistemaAvilaOS s = sistema(1024, 100, PoliticaDisco.FIFO);
        Proceso renombra = s.crearProcesoArchivo("ren", 5, 16, 500, 1, OperacionES.ACTUALIZAR, "/sistema/script.py", "admin",
                TipoNodo.ARCHIVO, 1, Permiso.PRIVADO, "arranque.py");
        s.iniciar(renombra);
        int fin = correrHasta(s, 1, 200, () -> renombra.getEstado() == EstadoProceso.TERMINADO);
        verificar(s.getArchivos().buscar("/sistema/arranque.py") != null, "actualizar con nombre nuevo renombra el archivo");
        igual(null, s.getArchivos().buscar("/sistema/script.py"), "y el nombre viejo ya no existe");
        verificar(fin < 20, "renombrar transfiere un solo bloque");

        Proceso reescribe = s.crearProcesoArchivo("esc", 5, 16, 500, 1, OperacionES.ACTUALIZAR, "/mbr.bin", "admin",
                TipoNodo.ARCHIVO, 1, Permiso.PRIVADO, null);
        s.iniciar(reescribe);
        correrHasta(s, 100, 200, () -> reescribe.getEstado() == EstadoProceso.TERMINADO);
        igual(EstadoProceso.TERMINADO, reescribe.getEstado(), "reescribir un archivo sin cambiarle el nombre también termina");
        verificar(s.getArchivos().buscar("/mbr.bin") != null, "y el archivo sigue ahí");
    }

    private static void rechazos() {
        seccion("Proceso de E/S: permisos de usuario");
        SistemaAvilaOS s = sistema(1024, 100, PoliticaDisco.FIFO);
        StringBuilder log = capturar(s);
        Proceso ajeno = s.crearProcesoArchivo("espia", 5, 16, 500, 1, OperacionES.LEER, "/usuarios/notas.txt", "user2",
                TipoNodo.ARCHIVO, 1, Permiso.PRIVADO, null);
        s.iniciar(ajeno);
        s.avanzar(1);
        igual(EstadoProceso.TERMINADO, ajeno.getEstado(), "un usuario que lee el archivo privado de otro termina sin llegar al disco");
        verificar(log.toString().contains("no puede hacer LEER /usuarios/notas.txt"), "el log explica el rechazo");
        verificar(log.toString().contains("es privado y pertenece a user1"), "con el motivo");
        igual(0, s.getDisco().getTamanioCola(), "la solicitud nunca llegó a la cola del disco");

        Proceso borraSistema = s.crearProcesoArchivo("malo", 5, 16, 500, 1, OperacionES.ELIMINAR, "/sistema/core.dll", "user1",
                TipoNodo.ARCHIVO, 1, Permiso.PRIVADO, null);
        s.iniciar(borraSistema);
        s.avanzar(2);
        verificar(s.getArchivos().buscar("/sistema/core.dll") != null, "un usuario no elimina archivos del sistema");

        Proceso propio = s.crearProcesoArchivo("bien", 5, 16, 500, 1, OperacionES.LEER, "/usuarios/notas.txt", "user1",
                TipoNodo.ARCHIVO, 1, Permiso.PRIVADO, null);
        s.iniciar(propio);
        correrHasta(s, 3, 100, () -> propio.getEstado() == EstadoProceso.TERMINADO);
        verificar(log.toString().contains("P3-bien pide al disco LEER"), "el dueño sí puede leer su archivo privado");

        Proceso noExiste = leer(s, "fantasma", "/usuarios/no_existe.txt", 1, 500);
        s.iniciar(noExiste);
        s.avanzar(200);
        verificar(log.toString().contains("no existe /usuarios/no_existe.txt"), "leer un archivo que no existe se rechaza con su motivo");
    }

    private static void muereEsperando() {
        seccion("Proceso de E/S: deadline mientras espera al disco");
        SistemaAvilaOS s = sistema(1024, 100, PoliticaDisco.FIFO);
        StringBuilder log = capturar(s);
        s.getArchivos().eliminar("/usuarios/img1.png");
        Proceso largo = leer(s, "largo", "/usuarios/data.csv", 1, 5000);
        Proceso crea = s.crearProcesoArchivo("crea", 5, 16, 6, 1, OperacionES.CREAR, "/usuarios/tmp.bin", "admin",
                TipoNodo.ARCHIVO, 4, Permiso.PRIVADO, null);
        s.iniciar(largo);
        s.iniciar(crea);
        correrHasta(s, 1, 10, () -> crea.getEstado() == EstadoProceso.TERMINADO);
        igual(EstadoProceso.TERMINADO, crea.getEstado(), "el proceso que espera en la cola del disco muere al llegar a 0 su deadline");
        igual(4, s.getDisco().getBloquesLibresArchivos(), "sus bloques reservados se liberan");
        correrHasta(s, 11, 200, () -> largo.getEstado() == EstadoProceso.TERMINADO);
        igual(null, s.getArchivos().buscar("/usuarios/tmp.bin"), "el archivo que nunca se creó no aparece");
        igual(4, s.getDisco().getBloquesLibresArchivos(), "ni retiene bloques");
        verificar(!log.toString().contains("P2-crea crea"), "no se registra una creación de un proceso muerto");
    }

    private static void politicaEnVivo() {
        seccion("Disco: cambio de política mientras corre el sistema");
        SistemaAvilaOS s = sistema(1024, 100, PoliticaDisco.FIFO);
        StringBuilder log = capturar(s);
        s.iniciar(leer(s, "a", "/usuarios/video.mp4", 1, 5000));
        s.iniciar(leer(s, "b", "/sistema/boot.bin", 1, 5000));
        s.iniciar(leer(s, "c", "/usuarios/data.csv", 1, 5000));
        correrHasta(s, 1, 6, () -> false);
        s.getDisco().setPolitica(PoliticaDisco.SCAN);
        igual(PoliticaDisco.SCAN, s.getDisco().getNombrePolitica(), "el disco acepta una política nueva en ejecución");
        correrHasta(s, 7, 600, () -> s.getDisco().getAtendidas() == 3);
        igual(3, s.getDisco().getAtendidas(), "se atienden las tres solicitudes");
        verificar(log.toString().contains("(FIFO)") && log.toString().contains("(SCAN)"), "el log registra la política con la que se atendió cada una");
        verificar(s.getDisco().getAtendidas(PoliticaDisco.FIFO) >= 1 && s.getDisco().getAtendidas(PoliticaDisco.SCAN) >= 1,
                "las métricas cuentan las atendidas por política");
    }

    private static void validaciones() {
        seccion("Proceso de E/S: validaciones al crearlo");
        SistemaAvilaOS s = sistema(64, 100, PoliticaDisco.FIFO);
        lanza(IllegalArgumentException.class, () -> leer(s, "", "/mbr.bin", 1, 500), "el nombre no puede estar vacío");
        lanza(IllegalArgumentException.class, () -> leer(s, "a,b", "/mbr.bin", 1, 500), "el nombre no puede tener comas");
        lanza(IllegalArgumentException.class, () -> leer(s, "x", "mbr.bin", 1, 500), "la ruta debe empezar con /");
        lanza(IllegalArgumentException.class, () -> leer(s, "x", "/mbr.bin", 0, 500), "las instrucciones deben ser al menos 1");
        lanza(IllegalArgumentException.class, () -> leer(s, "x", "/mbr.bin", 1, 0), "el deadline debe ser al menos 1");
        lanza(IllegalArgumentException.class, () -> s.crearProcesoArchivo("x", 0, 16, 500, 1, OperacionES.LEER, "/mbr.bin", "admin",
                TipoNodo.ARCHIVO, 1, Permiso.PRIVADO, null), "la prioridad debe estar entre 1 y 100");
        lanza(IllegalArgumentException.class, () -> s.crearProcesoArchivo("x", 5, 65, 500, 1, OperacionES.LEER, "/mbr.bin", "admin",
                TipoNodo.ARCHIVO, 1, Permiso.PRIVADO, null), "la memoria no puede superar la RAM del computador");
        lanza(IllegalArgumentException.class, () -> s.crearProcesoArchivo("x", 5, 16, 500, 1, OperacionES.SUSPENDER, "/mbr.bin", "admin",
                TipoNodo.ARCHIVO, 1, Permiso.PRIVADO, null), "suspender no es una operación que se pida a mano");
        lanza(IllegalArgumentException.class, () -> s.crearProcesoArchivo("x", 5, 16, 500, 1, OperacionES.CREAR, "/nuevo", "admin",
                TipoNodo.ARCHIVO, 0, Permiso.PRIVADO, null), "un archivo nuevo necesita al menos 1 bloque");
        lanza(IllegalArgumentException.class, () -> s.crearProcesoArchivo("x", 5, 16, 500, 1, OperacionES.ACTUALIZAR, "/mbr.bin", "admin",
                TipoNodo.ARCHIVO, 1, Permiso.PRIVADO, "a/b"), "el nombre nuevo no puede tener /");
        lanza(IllegalArgumentException.class, () -> SistemaAvilaOS.construir(
                DefinicionSistemaArchivos.desdeJson(PruebasArchivos.JSON_ENUNCIADO), 0, 3, PoliticaPlanificacion.FCFS, 10,
                PoliticaDisco.FIFO, 5), "la RAM debe ser mayor que 0");
        lanza(IllegalArgumentException.class, () -> SistemaAvilaOS.construir(
                DefinicionSistemaArchivos.desdeJson(PruebasArchivos.JSON_ENUNCIADO), 64, 3, PoliticaPlanificacion.FCFS, 10,
                PoliticaDisco.FIFO, 0), "el cabezal debe moverse al menos una posición por ciclo");
    }
}

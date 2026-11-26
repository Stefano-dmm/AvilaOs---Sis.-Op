package avilaos.pruebas;

import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.lanza;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.archivos.ControlAcceso;
import avilaos.archivos.DefinicionSistemaArchivos;
import avilaos.archivos.NodoArchivo;
import avilaos.archivos.Permiso;
import avilaos.archivos.Sesion;
import avilaos.archivos.SistemaArchivos;
import avilaos.archivos.TipoNodo;
import avilaos.disco.Disco;
import avilaos.disco.OperacionES;
import avilaos.disco.PoliticaDisco;
import avilaos.json.Json;

/**
 * El sistema de archivos: el JSON del enunciado (que llena el disco de 200 bloques), el CRUD con sus rechazos, la
 * creación en dos pasos, la asignación encadenada, los permisos de administrador y usuario y la ida y vuelta por JSON.
 */
public final class PruebasArchivos {

    private PruebasArchivos() {
    }

    /** El archivo del sistema de archivos del enunciado (los 9 archivos ocupan los 200 bloques). */
    static final String JSON_ENUNCIADO = """
            { "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
              "fileSystem": [
                { "path": "/mbr.bin", "type": "FILE", "startBlock": 0, "blocks": 11, "owner": "admin", "permissions": "public" },
                { "path": "/sistema", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                { "path": "/usuarios", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
                { "path": "/sistema/boot.bin", "type": "FILE", "startBlock": 11, "blocks": 23, "owner": "admin", "permissions": "public" },
                { "path": "/usuarios/notas.txt", "type": "FILE", "startBlock": 34, "blocks": 28, "owner": "user1", "permissions": "private" },
                { "path": "/sistema/script.py", "type": "FILE", "startBlock": 62, "blocks": 2, "owner": "admin", "permissions": "public" },
                { "path": "/sistema/core.dll", "type": "FILE", "startBlock": 64, "blocks": 31, "owner": "admin", "permissions": "public" },
                { "path": "/sistema/config.sys", "type": "FILE", "startBlock": 95, "blocks": 24, "owner": "admin", "permissions": "public" },
                { "path": "/usuarios/img1.png", "type": "FILE", "startBlock": 119, "blocks": 4, "owner": "user1", "permissions": "private" },
                { "path": "/usuarios/data.csv", "type": "FILE", "startBlock": 123, "blocks": 57, "owner": "user2", "permissions": "public" },
                { "path": "/usuarios/video.mp4", "type": "FILE", "startBlock": 180, "blocks": 20, "owner": "user1", "permissions": "public" }
              ] }
            """;

    /** Crea un disco y un sistema de archivos cargados con el JSON dado. */
    static SistemaArchivos cargarDesde(String json, int swap) {
        DefinicionSistemaArchivos definicion = DefinicionSistemaArchivos.desdeJson(json);
        Disco disco = new Disco(definicion.bloquesTotales(), swap, definicion.cabezalInicial(), 5, PoliticaDisco.FIFO);
        SistemaArchivos sistema = new SistemaArchivos(disco);
        sistema.cargar(definicion.entradas());
        return sistema;
    }

    public static void ejecutar() {
        cargaDelEnunciado();
        crearYEliminar();
        creacionEnDosPasos();
        rutasYNombres();
        permisos();
        guardadoEnJson();
        cargasInvalidas();
    }

    private static void cargaDelEnunciado() {
        seccion("Archivos: carga del JSON del enunciado");
        SistemaArchivos fs = cargarDesde(JSON_ENUNCIADO, 100);
        Disco disco = fs.getDisco();
        igual(200, disco.getBloquesArchivos(), "el disco tiene 200 bloques de archivos");
        igual(50, disco.getPosicion(), "el cabezal empieza en el bloque 50");
        igual(9, fs.todosLosArchivos().length, "se cargaron los 9 archivos");
        igual(0, disco.getBloquesLibresArchivos(), "los archivos llenan todo el disco");
        igual(100, disco.getBloquesLibresSwap(), "y el swap queda entero libre");
        NodoArchivo core = fs.buscar("/sistema/core.dll");
        igual(64, core.getPrimerBloque(), "core.dll empieza en el bloque 64");
        igual(31, core.getBloques(), "core.dll ocupa 31 bloques");
        igual("admin", core.getDueno(), "su dueño es admin");
        igual(Permiso.PUBLICO, core.getPermiso(), "y es público");
        igual(Permiso.PRIVADO, fs.buscar("/usuarios/notas.txt").getPermiso(), "notas.txt es privado");
        igual("/usuarios/notas.txt", fs.buscar("/usuarios/notas.txt").getRuta(), "cada nodo conoce su ruta completa");
        igual(65, disco.getSiguiente(64), "los bloques de un archivo cargado están encadenados");
        igual(-1, disco.getSiguiente(94), "el último bloque de core.dll cierra la cadena");
        igual(SistemaArchivos.CARGA_INICIAL, core.getCreador(), "lo cargado figura creado por la carga inicial");
        igual(null, fs.buscar("/usuarios/no_existe"), "una ruta inexistente devuelve null");
        igual(3, fs.hijosDe(fs.getRaiz()).length, "la raíz tiene el archivo mbr.bin y los directorios sistema y usuarios");
        igual(0, fs.bloqueDeReferencia(fs.buscar("/mbr.bin")), "el bloque de referencia de un archivo es su primer bloque");
        igual(11, fs.bloqueDeReferencia(fs.buscar("/sistema")), "el de un directorio es el de su primer archivo");
    }

    private static void crearYEliminar() {
        seccion("Archivos: crear y eliminar con el disco lleno");
        SistemaArchivos fs = cargarDesde(JSON_ENUNCIADO, 0);
        lanza(IllegalStateException.class, () -> fs.crear("/usuarios/nuevo.txt", TipoNodo.ARCHIVO, 1, "user1", Permiso.PRIVADO, "P1"),
                "no se crea un archivo si no hay bloques libres");
        igual(4, fs.eliminar("/usuarios/img1.png"), "eliminar img1.png libera sus 4 bloques");
        lanza(IllegalStateException.class, () -> fs.crear("/usuarios/grande.bin", TipoNodo.ARCHIVO, 5, "user1", Permiso.PRIVADO, "P1"),
                "5 bloques no caben en los 4 liberados");
        NodoArchivo nuevo = fs.crear("/usuarios/nuevo.txt", TipoNodo.ARCHIVO, 4, "user1", Permiso.PRIVADO, "P7-x");
        igual(119, nuevo.getPrimerBloque(), "el archivo nuevo reutiliza los bloques liberados");
        igual("P7-x", nuevo.getCreador(), "guarda el proceso que lo creó");
        igual(80, fs.eliminar("/sistema"), "eliminar un directorio elimina todo lo que contiene y libera sus 80 bloques");
        igual(null, fs.buscar("/sistema/core.dll"), "los archivos del directorio ya no existen");
        igual(80, fs.getDisco().getBloquesLibresArchivos(), "el disco recuperó esos bloques");
        NodoArchivo grande = fs.crear("/usuarios/grande.bin", TipoNodo.ARCHIVO, 30, "user1", Permiso.PRIVADO, "P8");
        igual(50, fs.getDisco().getBloquesLibresArchivos(), "el archivo grande ocupó 30 de los 80 bloques libres");
        int cadena = 0;
        for (int b = grande.getPrimerBloque(); b >= 0; b = fs.getDisco().getSiguiente(b)) {
            cadena++;
        }
        igual(30, cadena, "su cadena tiene 30 bloques aunque no sean contiguos");
        verificar(fs.getDisco().getSiguiente(33) == 62, "el bloque 33 apunta al 62: salta el hueco que dejó otro archivo");
        lanza(IllegalArgumentException.class, () -> fs.eliminar("/"), "no se puede eliminar la raíz");
        lanza(IllegalArgumentException.class, () -> fs.eliminar("/no/existe"), "no se elimina lo que no existe");
        igual(0, fs.crear("/usuarios/docs", TipoNodo.DIRECTORIO, 0, "user1", Permiso.PUBLICO, "P9").getBloques(),
                "un directorio no ocupa bloques");
    }

    private static void creacionEnDosPasos() {
        seccion("Archivos: la creación aparece en el árbol al terminar la solicitud");
        SistemaArchivos fs = cargarDesde(JSON_ENUNCIADO, 0);
        fs.eliminar("/usuarios/video.mp4");
        NodoArchivo pendiente = fs.reservar("/usuarios/cine.mp4", TipoNodo.ARCHIVO, 20, "user1", Permiso.PUBLICO, "P3");
        igual(180, pendiente.getPrimerBloque(), "al reservar ya se sabe qué bloque pedirá la solicitud");
        igual(0, fs.getDisco().getBloquesLibresArchivos(), "y los bloques quedan reservados");
        igual(null, fs.buscar("/usuarios/cine.mp4"), "pero el archivo todavía no está en el árbol");
        lanza(IllegalArgumentException.class, () -> fs.reservar("/usuarios/cine.mp4", TipoNodo.ARCHIVO, 1, "user1", Permiso.PUBLICO, "P4"),
                "no se puede reservar dos veces el mismo nombre");
        fs.confirmarCreacion(pendiente);
        igual(pendiente, fs.buscar("/usuarios/cine.mp4"), "al confirmar aparece en el árbol");
        fs.eliminar("/usuarios/cine.mp4");
        NodoArchivo descartado = fs.reservar("/usuarios/otro.bin", TipoNodo.ARCHIVO, 20, "user1", Permiso.PRIVADO, "P5");
        fs.cancelarCreacion(descartado);
        igual(20, fs.getDisco().getBloquesLibresArchivos(), "cancelar una creación pendiente libera sus bloques");
        NodoArchivo huerfano = fs.reservar("/usuarios/huerfano.bin", TipoNodo.ARCHIVO, 10, "user1", Permiso.PRIVADO, "P6");
        fs.eliminar("/usuarios");
        fs.confirmarCreacion(huerfano);
        igual(null, fs.buscar("/usuarios/huerfano.bin"), "si el directorio se eliminó mientras tanto, el archivo no aparece");
        igual(109, fs.getDisco().getBloquesLibresArchivos(), "y sus bloques se liberan: solo quedan ocupados mbr.bin (11) y /sistema (80)");
    }

    private static void rutasYNombres() {
        seccion("Archivos: rutas y nombres inválidos");
        SistemaArchivos fs = cargarDesde(JSON_ENUNCIADO, 0);
        lanza(IllegalArgumentException.class, () -> fs.buscar("sistema"), "la ruta debe empezar con /");
        lanza(IllegalArgumentException.class, () -> fs.buscar("/a//b"), "no se aceptan segmentos vacíos");
        lanza(IllegalArgumentException.class, () -> fs.buscar("/../x"), "no se acepta ..");
        lanza(IllegalArgumentException.class, () -> fs.buscar(""), "la ruta no puede estar vacía");
        lanza(IllegalArgumentException.class, () -> fs.buscar(null), "ni ser null");
        igual("/sistema", SistemaArchivos.normalizar("/sistema/"), "una barra final se ignora");
        lanza(IllegalArgumentException.class, () -> fs.crear("/sistema/" + "x".repeat(61), TipoNodo.DIRECTORIO, 0, "admin", Permiso.PUBLICO, "P1"),
                "un nombre de más de 60 caracteres se rechaza");
        lanza(IllegalArgumentException.class, () -> fs.crear("/falta/dir/a.txt", TipoNodo.DIRECTORIO, 0, "admin", Permiso.PUBLICO, "P1"),
                "no se crea dentro de un directorio que no existe");
        lanza(IllegalArgumentException.class, () -> fs.crear("/mbr.bin/a", TipoNodo.DIRECTORIO, 0, "admin", Permiso.PUBLICO, "P1"),
                "no se crea dentro de un archivo");
        lanza(IllegalArgumentException.class, () -> fs.crear("/sistema", TipoNodo.DIRECTORIO, 0, "admin", Permiso.PUBLICO, "P1"),
                "no se crea algo que ya existe");
        lanza(IllegalArgumentException.class, () -> fs.crear("/sistema/z", TipoNodo.ARCHIVO, 0, "admin", Permiso.PUBLICO, "P1"),
                "un archivo de 0 bloques es inválido");
        lanza(IllegalArgumentException.class, () -> fs.crear("/sistema/z", TipoNodo.ARCHIVO, 201, "admin", Permiso.PUBLICO, "P1"),
                "un archivo más grande que el disco es inválido");
        fs.renombrar("/sistema/script.py", "arranque.py");
        verificar(fs.buscar("/sistema/arranque.py") != null && fs.buscar("/sistema/script.py") == null, "renombrar cambia la ruta");
        lanza(IllegalArgumentException.class, () -> fs.renombrar("/sistema/arranque.py", "boot.bin"), "no se renombra a un nombre ya usado");
        lanza(IllegalArgumentException.class, () -> fs.renombrar("/sistema/arranque.py", "a/b"), "el nombre nuevo no puede tener /");
        lanza(IllegalArgumentException.class, () -> fs.renombrar("/", "x"), "no se renombra la raíz");
        lanza(IllegalArgumentException.class, () -> fs.renombrar("/no/existe", "x"), "no se renombra lo que no existe");
        igual(1, fs.bloquesATransferir(fs.buscar("/sistema/arranque.py"), OperacionES.ACTUALIZAR, true), "renombrar cuesta un bloque");
        igual(2, fs.bloquesATransferir(fs.buscar("/sistema/arranque.py"), OperacionES.ACTUALIZAR, false), "reescribir cuesta todos sus bloques");
        igual(80, fs.bloquesATransferir(fs.buscar("/sistema"), OperacionES.ELIMINAR, false), "eliminar un directorio cuesta los bloques de todo lo que contiene");
    }

    private static void permisos() {
        seccion("Archivos: administrador y usuario");
        SistemaArchivos fs = cargarDesde(JSON_ENUNCIADO, 0);
        Sesion admin = Sesion.administrador();
        Sesion user1 = Sesion.usuario("user1");
        NodoArchivo notas = fs.buscar("/usuarios/notas.txt");
        NodoArchivo datos = fs.buscar("/usuarios/data.csv");
        NodoArchivo boot = fs.buscar("/sistema/boot.bin");
        igual(null, ControlAcceso.motivoDeRechazo(admin, OperacionES.ELIMINAR, boot, false), "el administrador puede eliminar archivos del sistema");
        igual(null, ControlAcceso.motivoDeRechazo(user1, OperacionES.LEER, notas, false), "un usuario lee sus archivos privados");
        igual(null, ControlAcceso.motivoDeRechazo(user1, OperacionES.LEER, datos, false), "un usuario lee archivos públicos de otros");
        verificar(ControlAcceso.motivoDeRechazo(Sesion.usuario("user2"), OperacionES.LEER, notas, false) != null,
                "un usuario no lee archivos privados de otros");
        verificar(ControlAcceso.motivoDeRechazo(user1, OperacionES.ELIMINAR, boot, false).contains("admin"),
                "no puede eliminar archivos del sistema y el motivo dice de quién son");
        verificar(ControlAcceso.motivoDeRechazo(user1, OperacionES.ACTUALIZAR, datos, false) != null, "no modifica archivos de otro usuario");
        igual(null, ControlAcceso.motivoDeRechazo(user1, OperacionES.ACTUALIZAR, notas, false), "sí reescribe los propios");
        verificar(ControlAcceso.motivoDeRechazo(user1, OperacionES.ACTUALIZAR, notas, true) != null, "pero no cambia nombres: eso es del administrador");
        verificar(ControlAcceso.motivoDeRechazo(user1, OperacionES.CREAR, fs.buscar("/sistema"), false) != null,
                "no crea en directorios del sistema");
        verificar(ControlAcceso.motivoDeRechazo(user1, OperacionES.CREAR, null, false) != null, "un objetivo que no existe se rechaza");
        NodoArchivo propio = fs.crear("/usuarios/user1", TipoNodo.DIRECTORIO, 0, "user1", Permiso.PRIVADO, "P1");
        igual(null, ControlAcceso.motivoDeRechazo(user1, OperacionES.CREAR, propio, false), "sí crea dentro de su propio directorio");
        verificar(ControlAcceso.motivoDeRechazo(user1, OperacionES.SUSPENDER, notas, false) != null, "un usuario no suspende procesos");
    }

    private static void guardadoEnJson() {
        seccion("Archivos: guardar y volver a cargar");
        SistemaArchivos fs = cargarDesde(JSON_ENUNCIADO, 0);
        fs.eliminar("/usuarios/img1.png");
        fs.eliminar("/sistema/script.py");
        NodoArchivo fragmentado = fs.crear("/usuarios/mixto.bin", TipoNodo.ARCHIVO, 6, "user2", Permiso.PRIVADO, "P2");
        String json = Json.escribir(fs.aJson());
        SistemaArchivos copia = cargarDesde(json, 0);
        igual(fs.todosLosArchivos().length, copia.todosLosArchivos().length, "al recargar hay los mismos archivos");
        NodoArchivo recargado = copia.buscar("/usuarios/mixto.bin");
        igual(fragmentado.getPrimerBloque(), recargado.getPrimerBloque(), "el archivo fragmentado conserva su primer bloque");
        boolean mismaCadena = true;
        int a = fragmentado.getPrimerBloque();
        int b = recargado.getPrimerBloque();
        while (a >= 0 && b >= 0) {
            mismaCadena &= a == b;
            a = fs.getDisco().getSiguiente(a);
            b = copia.getDisco().getSiguiente(b);
        }
        verificar(mismaCadena && a == b, "y también la cadena entera de bloques, aunque no sea contigua");
        igual("user2", recargado.getDueno(), "conserva su dueño");
        igual(Permiso.PRIVADO, recargado.getPermiso(), "y su permiso");
        igual(fs.getDisco().getBloquesLibresArchivos(), copia.getDisco().getBloquesLibresArchivos(), "ocupan los mismos bloques");
    }

    private static void cargasInvalidas() {
        seccion("Archivos: JSON inválido al cargar");
        lanza(IllegalArgumentException.class, () -> DefinicionSistemaArchivos.desdeJson("{\"diskConfig\": {\"totalBlocks\": 5, \"initialHead\": 0}, \"fileSystem\": []}"),
                "un disco de menos de 10 bloques se rechaza");
        lanza(IllegalArgumentException.class, () -> DefinicionSistemaArchivos.desdeJson("{\"diskConfig\": {\"totalBlocks\": 200, \"initialHead\": 200}, \"fileSystem\": []}"),
                "el cabezal inicial no puede estar fuera del disco");
        lanza(IllegalArgumentException.class, () -> DefinicionSistemaArchivos.desdeJson("{\"fileSystem\": []}"), "falta diskConfig");
        String base = "{\"diskConfig\": {\"totalBlocks\": 100, \"initialHead\": 0}, \"fileSystem\": [%s]}";
        lanza(IllegalArgumentException.class, () -> cargarDesde(base.formatted(
                "{\"path\": \"/a\", \"type\": \"FILE\", \"startBlock\": 0, \"blocks\": 10, \"owner\": \"x\"},"
                        + "{\"path\": \"/b\", \"type\": \"FILE\", \"startBlock\": 5, \"blocks\": 10, \"owner\": \"x\"}"), 0),
                "dos archivos no pueden ocupar los mismos bloques");
        lanza(IllegalArgumentException.class, () -> cargarDesde(base.formatted(
                "{\"path\": \"/a\", \"type\": \"FILE\", \"startBlock\": 95, \"blocks\": 10, \"owner\": \"x\"}"), 0),
                "un archivo no puede salirse del disco");
        lanza(IllegalArgumentException.class, () -> cargarDesde(base.formatted(
                "{\"path\": \"/d/a\", \"type\": \"FILE\", \"startBlock\": 0, \"blocks\": 1, \"owner\": \"x\"}"), 0),
                "un archivo dentro de un directorio que no existe");
        lanza(IllegalArgumentException.class, () -> cargarDesde(base.formatted(
                "{\"path\": \"/a\", \"type\": \"LINK\", \"owner\": \"x\"}"), 0), "tipo desconocido");
        lanza(IllegalArgumentException.class, () -> cargarDesde(base.formatted(
                "{\"path\": \"/a\", \"type\": \"FILE\", \"startBlock\": 0, \"blocks\": 1, \"owner\": \"x\", \"permissions\": \"todos\"}"), 0),
                "permiso desconocido");
        lanza(IllegalArgumentException.class, () -> cargarDesde(base.formatted(
                "{\"path\": \"/a\", \"type\": \"FILE\", \"startBlock\": 0, \"blocks\": \"tres\", \"owner\": \"x\"}"), 0),
                "los bloques deben ser un número");
        lanza(IllegalArgumentException.class, () -> cargarDesde(base.formatted(
                "{\"path\": \"/a\", \"type\": \"FILE\", \"startBlock\": 0, \"blocks\": 1, \"owner\": \"x\"},"
                        + "{\"path\": \"/a\", \"type\": \"FILE\", \"startBlock\": 3, \"blocks\": 1, \"owner\": \"x\"}"), 0),
                "dos elementos con la misma ruta");
        igual(0, cargarDesde(base.formatted(""), 0).todosLosArchivos().length, "un sistema de archivos vacío es válido");
    }
}

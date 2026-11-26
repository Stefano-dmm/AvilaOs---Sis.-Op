package avilaos.archivos;

import avilaos.disco.Disco;
import avilaos.disco.OperacionES;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.json.JsonArreglo;
import avilaos.json.JsonObjeto;
import java.util.concurrent.Semaphore;

/**
 * El sistema de archivos jerárquico: un árbol de directorios y archivos cuyos bloques se asignan en el disco de forma
 * encadenada. Es parte del sistema operativo: la interfaz solo lo observa y las operaciones las ejecuta el disco
 * cuando atiende la solicitud de un proceso de E/S.
 *
 * <p><b>Crear en dos pasos.</b> Reservar los bloques ocurre cuando el proceso genera su solicitud (para saber a qué
 * bloque debe ir el cabezal y para rechazar de inmediato si no hay espacio); el archivo aparece en el árbol recién
 * cuando el disco termina de atender la solicitud (confirmarCreacion).
 *
 * <p><b>Concurrencia.</b> El disco (su hilo) modifica el árbol al terminar una solicitud mientras los computadores y
 * la interfaz lo leen. El candado protege el árbol y los pendientes; el orden de candados es siempre este y luego el
 * del disco, nunca al revés.
 */
public class SistemaArchivos {

    /** Largo máximo del nombre de un archivo o directorio. */
    public static final int MAX_NOMBRE = 60;
    /** Texto de creador para lo que existe desde la carga inicial. */
    public static final String CARGA_INICIAL = "carga inicial";

    /** Disco donde viven los bloques de los archivos. */
    private final Disco disco;
    /** Raíz del árbol: el directorio "/", del administrador y público. */
    private final NodoArchivo raiz = new NodoArchivo(0, "", TipoNodo.DIRECTORIO, Sesion.ADMIN, Permiso.PUBLICO, -1, 0, "sistema");
    /** Elementos con bloques ya reservados cuya solicitud todavía no terminó (aún no están en el árbol). */
    private final ListaEnlazada<NodoArchivo> pendientes = new ListaEnlazada<>();
    /** Ids dados a los nodos; el 0 es la raíz. */
    private int contadorIds;
    /** Semaphore de Java que protege el árbol y la lista de pendientes. */
    private final Semaphore candado = new Semaphore(1);

    /** Crea un sistema de archivos vacío (solo la raíz) sobre el disco dado. */
    public SistemaArchivos(Disco disco) {
        if (disco == null) {
            throw new IllegalArgumentException("El sistema de archivos necesita un disco.");
        }
        this.disco = disco;
    }

    public NodoArchivo getRaiz() {
        return raiz;
    }

    public Disco getDisco() {
        return disco;
    }

    // ---- rutas ----

    /** Normaliza la ruta: debe empezar con "/", sin segmentos vacíos, "." ni ".."; devuelve "/" o la ruta sin barra final. */
    public static String normalizar(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            throw new IllegalArgumentException("La ruta no puede estar vacía.");
        }
        String r = ruta.trim();
        if (!r.startsWith("/")) {
            throw new IllegalArgumentException("La ruta debe empezar con \"/\": " + r);
        }
        if (r.equals("/")) {
            return r;
        }
        String sinFinal = r.endsWith("/") ? r.substring(0, r.length() - 1) : r;
        String[] partes = sinFinal.substring(1).split("/", -1);
        for (String parte : partes) {
            validarNombre(parte);
        }
        return sinFinal;
    }

    /** Un nombre válido no está vacío, no es "." ni "..", no tiene "/" ni caracteres de control y mide a lo sumo MAX_NOMBRE. */
    public static void validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank() || nombre.equals(".") || nombre.equals("..")) {
            throw new IllegalArgumentException("Nombre inválido: \"" + (nombre == null ? "" : nombre) + "\".");
        }
        if (nombre.length() > MAX_NOMBRE) {
            throw new IllegalArgumentException("El nombre no puede pasar de " + MAX_NOMBRE + " caracteres.");
        }
        for (int i = 0; i < nombre.length(); i++) {
            char c = nombre.charAt(i);
            if (c == '/' || c == '\\' || c < 0x20) {
                throw new IllegalArgumentException("El nombre \"" + nombre + "\" tiene caracteres no permitidos.");
            }
        }
    }

    /** Busca el nodo de la ruta; null si no existe. Una ruta mal escrita lanza IllegalArgumentException. */
    public NodoArchivo buscar(String ruta) {
        String limpia = normalizar(ruta);
        candado.acquireUninterruptibly();
        try {
            return buscarInterno(limpia);
        } finally {
            candado.release();
        }
    }

    private NodoArchivo buscarInterno(String rutaNormalizada) {
        if (rutaNormalizada.equals("/")) {
            return raiz;
        }
        NodoArchivo actual = raiz;
        for (String parte : rutaNormalizada.substring(1).split("/")) {
            actual = hijoNombrado(actual, parte);
            if (actual == null) {
                return null;
            }
        }
        return actual;
    }

    private static NodoArchivo hijoNombrado(NodoArchivo directorio, String nombre) {
        Nodo<NodoArchivo> actual = directorio.getHijos().getCabeza();
        while (actual != null) {
            if (actual.getDato().getNombre().equals(nombre)) {
                return actual.getDato();
            }
            actual = actual.getSiguiente();
        }
        return null;
    }

    // ---- crear ----

    /**
     * Reserva un archivo o directorio nuevo: valida la ruta, que el padre exista y sea un directorio, que el nombre no esté
     * usado y que haya bloques libres suficientes. El elemento queda pendiente hasta confirmarCreacion.
     *
     * @throws IllegalArgumentException si la ruta, el nombre o el tamaño son inválidos
     * @throws IllegalStateException si no hay bloques libres suficientes
     */
    public NodoArchivo reservar(String ruta, TipoNodo tipo, int bloques, String dueno, Permiso permiso, String creador) {
        return crearInterno(ruta, tipo, bloques, -1, null, dueno, permiso, creador, true);
    }

    /** Reserva y confirma de una vez (la carga inicial y las pruebas). */
    public NodoArchivo crear(String ruta, TipoNodo tipo, int bloques, String dueno, Permiso permiso, String creador) {
        return crearInterno(ruta, tipo, bloques, -1, null, dueno, permiso, creador, false);
    }

    private NodoArchivo crearInterno(String ruta, TipoNodo tipo, int bloques, int inicio, int[] cadena, String dueno,
                                     Permiso permiso, String creador, boolean pendiente) {
        String limpia = normalizar(ruta);
        if (limpia.equals("/")) {
            throw new IllegalArgumentException("La raíz ya existe.");
        }
        if (dueno == null || dueno.isBlank()) {
            throw new IllegalArgumentException("Falta el dueño de " + limpia + ".");
        }
        if (tipo == TipoNodo.ARCHIVO && (bloques < 1 || bloques > disco.getBloquesArchivos())) {
            throw new IllegalArgumentException("El tamaño de " + limpia + " debe estar entre 1 y "
                    + disco.getBloquesArchivos() + " bloques.");
        }
        int corte = limpia.lastIndexOf('/');
        String rutaPadre = corte == 0 ? "/" : limpia.substring(0, corte);
        String nombre = limpia.substring(corte + 1);
        candado.acquireUninterruptibly();
        try {
            NodoArchivo padre = buscarInterno(rutaPadre);
            if (padre == null || !estaEnArbol(padre)) {
                throw new IllegalArgumentException("El directorio " + rutaPadre + " no existe.");
            }
            if (!padre.esDirectorio()) {
                throw new IllegalArgumentException(rutaPadre + " es un archivo, no un directorio.");
            }
            if (hijoNombrado(padre, nombre) != null || estaPendiente(padre, nombre)) {
                throw new IllegalArgumentException("Ya existe " + limpia + ".");
            }
            int id = ++contadorIds;
            int primer = -1;
            int ocupados = 0;
            if (tipo == TipoNodo.ARCHIVO) {
                ocupados = cadena != null ? cadena.length : bloques;
                if (cadena != null) {
                    primer = disco.reservarCadena(cadena, id) ? cadena[0] : -1;
                } else if (inicio >= 0) {
                    primer = disco.reservarBloquesDesde(inicio, bloques, id) ? inicio : -1;
                } else {
                    primer = disco.reservarBloquesArchivo(bloques, id);
                }
                if (primer < 0) {
                    throw new IllegalStateException("No hay bloques libres suficientes para " + limpia + " (pide "
                            + ocupados + ", libres " + disco.getBloquesLibresArchivos() + ").");
                }
            }
            NodoArchivo nodo = new NodoArchivo(id, nombre, tipo, dueno.trim(), permiso == null ? Permiso.PRIVADO : permiso,
                    primer, ocupados, creador == null ? "sistema" : creador);
            nodo.setPadre(padre);
            if (pendiente) {
                pendientes.agregarAlFinal(nodo);
            } else {
                padre.getHijos().agregarAlFinal(nodo);
            }
            return nodo;
        } finally {
            candado.release();
        }
    }

    private boolean estaPendiente(NodoArchivo padre, String nombre) {
        Nodo<NodoArchivo> actual = pendientes.getCabeza();
        while (actual != null) {
            if (actual.getDato().getPadre() == padre && actual.getDato().getNombre().equals(nombre)) {
                return true;
            }
            actual = actual.getSiguiente();
        }
        return false;
    }

    /** Verdadero si el nodo sigue colgando de la raíz (no fue eliminado ni está pendiente). */
    private boolean estaEnArbol(NodoArchivo nodo) {
        NodoArchivo actual = nodo;
        while (actual != raiz) {
            NodoArchivo padre = actual.getPadre();
            if (padre == null || !padre.getHijos().contiene(actual)) {
                return false;
            }
            actual = padre;
        }
        return true;
    }

    /** El disco terminó de atender la creación: el elemento pasa al árbol. Si su directorio ya no existe, libera sus bloques. */
    public void confirmarCreacion(NodoArchivo nodo) {
        candado.acquireUninterruptibly();
        try {
            if (!pendientes.eliminar(nodo)) {
                return;
            }
            if (nodo.getPadre() != null && estaEnArbol(nodo.getPadre()) && hijoNombrado(nodo.getPadre(), nodo.getNombre()) == null) {
                nodo.getPadre().getHijos().agregarAlFinal(nodo);
            } else if (nodo.getPrimerBloque() >= 0) {
                disco.liberarBloques(nodo.getPrimerBloque());
            }
        } finally {
            candado.release();
        }
    }

    /** Se descarta una creación pendiente (el proceso murió antes de que el disco la atendiera): libera sus bloques. */
    public void cancelarCreacion(NodoArchivo nodo) {
        candado.acquireUninterruptibly();
        try {
            if (pendientes.eliminar(nodo) && nodo.getPrimerBloque() >= 0) {
                disco.liberarBloques(nodo.getPrimerBloque());
            }
        } finally {
            candado.release();
        }
    }

    // ---- actualizar y eliminar ----

    /** Cambia el nombre del archivo o directorio de la ruta. No se puede renombrar la raíz ni usar un nombre ya usado en el directorio. */
    public void renombrar(String ruta, String nuevoNombre) {
        validarNombre(nuevoNombre);
        String limpia = normalizar(ruta);
        candado.acquireUninterruptibly();
        try {
            NodoArchivo nodo = buscarInterno(limpia);
            if (nodo == null) {
                throw new IllegalArgumentException("No existe " + limpia + ".");
            }
            if (nodo == raiz) {
                throw new IllegalArgumentException("No se puede renombrar la raíz.");
            }
            String nuevo = nuevoNombre.trim();
            NodoArchivo igual = hijoNombrado(nodo.getPadre(), nuevo);
            if (igual != null && igual != nodo) {
                throw new IllegalArgumentException("Ya existe " + (nodo.getPadre() == raiz ? "" : nodo.getPadre().getRuta()) + "/" + nuevo + ".");
            }
            nodo.setNombre(nuevo);
        } finally {
            candado.release();
        }
    }

    /** Elimina el elemento y todo lo que contiene, liberando sus bloques; devuelve cuántos bloques liberó. */
    public int eliminar(String ruta) {
        String limpia = normalizar(ruta);
        candado.acquireUninterruptibly();
        try {
            NodoArchivo nodo = buscarInterno(limpia);
            if (nodo == null) {
                throw new IllegalArgumentException("No existe " + limpia + ".");
            }
            if (nodo == raiz) {
                throw new IllegalArgumentException("No se puede eliminar la raíz.");
            }
            nodo.getPadre().getHijos().eliminar(nodo);
            return liberarSubarbol(nodo);
        } finally {
            candado.release();
        }
    }

    private int liberarSubarbol(NodoArchivo nodo) {
        int liberados = 0;
        if (nodo.getPrimerBloque() >= 0) {
            liberados += disco.liberarBloques(nodo.getPrimerBloque());
        }
        Nodo<NodoArchivo> hijo = nodo.getHijos().getCabeza();
        while (hijo != null) {
            liberados += liberarSubarbol(hijo.getDato());
            hijo = hijo.getSiguiente();
        }
        return liberados;
    }

    // ---- datos para el disco y la interfaz ----

    /** Bloque al que debe ir el cabezal para operar sobre el nodo: el primero de un archivo, o el del primer archivo que contiene un directorio (0 si no tiene). */
    public int bloqueDeReferencia(NodoArchivo nodo) {
        candado.acquireUninterruptibly();
        try {
            return referencia(nodo);
        } finally {
            candado.release();
        }
    }

    private static int referencia(NodoArchivo nodo) {
        NodoArchivo archivo = primerArchivo(nodo);
        return archivo == null ? 0 : archivo.getPrimerBloque();
    }

    /** El nodo mismo si es un archivo, o el primer archivo que se encuentra dentro de un directorio; null si no hay ninguno. */
    private static NodoArchivo primerArchivo(NodoArchivo nodo) {
        if (!nodo.esDirectorio()) {
            return nodo;
        }
        Nodo<NodoArchivo> hijo = nodo.getHijos().getCabeza();
        while (hijo != null) {
            NodoArchivo encontrado = primerArchivo(hijo.getDato());
            if (encontrado != null) {
                return encontrado;
            }
            hijo = hijo.getSiguiente();
        }
        return null;
    }

    /** Bloques que el disco lee o escribe al hacer la operación sobre el nodo (siempre al menos 1). */
    public int bloquesATransferir(NodoArchivo nodo, OperacionES operacion, boolean renombra) {
        candado.acquireUninterruptibly();
        try {
            if (operacion == OperacionES.ACTUALIZAR && renombra) {
                return 1;
            }
            return Math.max(1, contarBloques(nodo));
        } finally {
            candado.release();
        }
    }

    private static int contarBloques(NodoArchivo nodo) {
        int total = nodo.getBloques();
        Nodo<NodoArchivo> hijo = nodo.getHijos().getCabeza();
        while (hijo != null) {
            total += contarBloques(hijo.getDato());
            hijo = hijo.getSiguiente();
        }
        return total;
    }

    /** Foto de los hijos de un directorio, en orden de creación. */
    public NodoArchivo[] hijosDe(NodoArchivo directorio) {
        candado.acquireUninterruptibly();
        try {
            NodoArchivo[] copia = new NodoArchivo[directorio.getHijos().getTamanio()];
            int i = 0;
            Nodo<NodoArchivo> actual = directorio.getHijos().getCabeza();
            while (actual != null) {
                copia[i++] = actual.getDato();
                actual = actual.getSiguiente();
            }
            return copia;
        } finally {
            candado.release();
        }
    }

    /** Foto de todos los archivos (no los directorios) del árbol, recorriéndolo en profundidad: lo que muestra la tabla de asignación. */
    public NodoArchivo[] todosLosArchivos() {
        candado.acquireUninterruptibly();
        try {
            ListaEnlazada<NodoArchivo> acumulado = new ListaEnlazada<>();
            recolectarArchivos(raiz, acumulado);
            NodoArchivo[] copia = new NodoArchivo[acumulado.getTamanio()];
            int i = 0;
            Nodo<NodoArchivo> actual = acumulado.getCabeza();
            while (actual != null) {
                copia[i++] = actual.getDato();
                actual = actual.getSiguiente();
            }
            return copia;
        } finally {
            candado.release();
        }
    }

    private static void recolectarArchivos(NodoArchivo nodo, ListaEnlazada<NodoArchivo> destino) {
        Nodo<NodoArchivo> hijo = nodo.getHijos().getCabeza();
        while (hijo != null) {
            if (hijo.getDato().esDirectorio()) {
                recolectarArchivos(hijo.getDato(), destino);
            } else {
                destino.agregarAlFinal(hijo.getDato());
            }
            hijo = hijo.getSiguiente();
        }
    }

    /** Busca un archivo del árbol por el id que es dueño de sus bloques; null si no existe (por ejemplo, ya fue eliminado). */
    public NodoArchivo buscarPorId(int id) {
        NodoArchivo[] archivos = todosLosArchivos();
        for (NodoArchivo a : archivos) {
            if (a.getId() == id) {
                return a;
            }
        }
        return null;
    }

    // ---- persistencia en JSON ----

    /** Convierte el sistema de archivos al JSON del enunciado: diskConfig con el tamaño y el cabezal, y fileSystem con cada elemento. */
    public JsonObjeto aJson() {
        JsonObjeto resultado = new JsonObjeto();
        resultado.poner("diskConfig", new JsonObjeto()
                .poner("totalBlocks", (long) disco.getBloquesArchivos())
                .poner("initialHead", (long) disco.getPosicion()));
        JsonArreglo lista = new JsonArreglo();
        candado.acquireUninterruptibly();
        try {
            escribirHijos(raiz, lista);
        } finally {
            candado.release();
        }
        resultado.poner("fileSystem", lista);
        return resultado;
    }

    private void escribirHijos(NodoArchivo directorio, JsonArreglo lista) {
        Nodo<NodoArchivo> hijo = directorio.getHijos().getCabeza();
        while (hijo != null) {
            NodoArchivo n = hijo.getDato();
            JsonObjeto entrada = new JsonObjeto();
            entrada.poner("path", n.getRuta());
            entrada.poner("type", n.esDirectorio() ? "DIRECTORY" : "FILE");
            if (!n.esDirectorio()) {
                entrada.poner("startBlock", (long) n.getPrimerBloque());
                entrada.poner("blocks", (long) n.getBloques());
            }
            entrada.poner("owner", n.getDueno());
            entrada.poner("permissions", n.getPermiso() == Permiso.PUBLICO ? "public" : "private");
            if (!n.esDirectorio() && !esContiguo(n)) {
                JsonArreglo cadena = new JsonArreglo();
                int b = n.getPrimerBloque();
                while (b >= 0) {
                    cadena.agregar((long) b);
                    b = disco.getSiguiente(b);
                }
                entrada.poner("chain", cadena);
            }
            lista.agregar(entrada);
            if (n.esDirectorio()) {
                escribirHijos(n, lista);
            }
            hijo = hijo.getSiguiente();
        }
    }

    private boolean esContiguo(NodoArchivo n) {
        int b = n.getPrimerBloque();
        for (int i = 1; i < n.getBloques(); i++) {
            if (disco.getSiguiente(b) != b + 1) {
                return false;
            }
            b++;
        }
        return true;
    }

    /**
     * Carga las entradas de un JSON de sistema de archivos: primero los directorios (de los más cercanos a la raíz a los más
     * profundos) y luego los archivos, cada uno en los bloques que dice el JSON. Cualquier dato inválido lanza
     * IllegalArgumentException con la ruta del elemento.
     */
    public void cargar(JsonArreglo entradas) {
        int mayorProfundidad = 1;
        for (int i = 0; i < entradas.tamanio(); i++) {
            String ruta = normalizar(entradas.objeto(i).texto("path"));
            mayorProfundidad = Math.max(mayorProfundidad, profundidad(ruta));
        }
        for (int nivel = 1; nivel <= mayorProfundidad; nivel++) {
            for (int i = 0; i < entradas.tamanio(); i++) {
                JsonObjeto e = entradas.objeto(i);
                String ruta = normalizar(e.texto("path"));
                if (tipoDe(e, ruta) == TipoNodo.DIRECTORIO && profundidad(ruta) == nivel) {
                    crearInterno(ruta, TipoNodo.DIRECTORIO, 0, -1, null, e.texto("owner", Sesion.ADMIN),
                            permisoDe(e, ruta), CARGA_INICIAL, false);
                }
            }
        }
        for (int i = 0; i < entradas.tamanio(); i++) {
            JsonObjeto e = entradas.objeto(i);
            String ruta = normalizar(e.texto("path"));
            if (tipoDe(e, ruta) == TipoNodo.ARCHIVO) {
                cargarArchivo(e, ruta);
            }
        }
    }

    private void cargarArchivo(JsonObjeto e, String ruta) {
        int bloques = e.entero("blocks");
        JsonArreglo cadena = e.arregloOpcional("chain");
        int[] bloquesDeCadena = null;
        if (cadena != null) {
            bloquesDeCadena = new int[cadena.tamanio()];
            for (int j = 0; j < bloquesDeCadena.length; j++) {
                if (!(cadena.obtener(j) instanceof Long b) || b < 0 || b > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("La cadena de bloques de " + ruta + " debe tener números enteros.");
                }
                bloquesDeCadena[j] = (int) (long) b;
            }
            if (bloquesDeCadena.length != bloques) {
                throw new IllegalArgumentException("La cadena de " + ruta + " tiene " + bloquesDeCadena.length
                        + " bloques y dice blocks=" + bloques + ".");
            }
        }
        int inicio = e.entero("startBlock", -1);
        if (bloquesDeCadena == null && (inicio < 0 || inicio + bloques > disco.getBloquesArchivos())) {
            throw new IllegalArgumentException("Los bloques de " + ruta + " (desde " + inicio + ", cantidad " + bloques
                    + ") no caben en el disco.");
        }
        try {
            crearInterno(ruta, TipoNodo.ARCHIVO, bloques, inicio, bloquesDeCadena, e.texto("owner", Sesion.ADMIN),
                    permisoDe(e, ruta), CARGA_INICIAL, false);
        } catch (IllegalStateException ex) {
            throw new IllegalArgumentException("Los bloques de " + ruta + " ya están ocupados por otro archivo o no caben en el disco.", ex);
        }
    }

    private static int profundidad(String rutaNormalizada) {
        int cuenta = 0;
        for (int i = 0; i < rutaNormalizada.length(); i++) {
            if (rutaNormalizada.charAt(i) == '/') {
                cuenta++;
            }
        }
        return cuenta;
    }

    private static TipoNodo tipoDe(JsonObjeto e, String ruta) {
        String tipo = e.texto("type");
        if (tipo.equalsIgnoreCase("FILE")) {
            return TipoNodo.ARCHIVO;
        }
        if (tipo.equalsIgnoreCase("DIRECTORY")) {
            return TipoNodo.DIRECTORIO;
        }
        throw new IllegalArgumentException("El tipo de " + ruta + " debe ser FILE o DIRECTORY, no \"" + tipo + "\".");
    }

    private static Permiso permisoDe(JsonObjeto e, String ruta) {
        String texto = e.texto("permissions", "private");
        if (texto.equalsIgnoreCase("public") || texto.equalsIgnoreCase("publico")) {
            return Permiso.PUBLICO;
        }
        if (texto.equalsIgnoreCase("private") || texto.equalsIgnoreCase("privado")) {
            return Permiso.PRIVADO;
        }
        throw new IllegalArgumentException("Los permisos de " + ruta + " deben ser public o private, no \"" + texto + "\".");
    }
}

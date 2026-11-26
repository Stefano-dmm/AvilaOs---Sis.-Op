package avilaos.archivos;

import avilaos.estructuras.ListaEnlazada;

/**
 * Un elemento del sistema de archivos: un archivo (con su cadena de bloques en el disco) o un directorio (con sus
 * hijos). Forma un árbol: cada nodo conoce a su padre y a sus hijos. Lo modifica solo el SistemaArchivos, con su candado.
 */
public class NodoArchivo {

    /** Id único dentro del sistema de archivos; también es el dueño de sus bloques en el mapa del disco. */
    private final int id;
    /** Nombre del elemento (sin la ruta). */
    private String nombre;
    /** Archivo o directorio. */
    private final TipoNodo tipo;
    /** Directorio que lo contiene; null en la raíz. */
    private NodoArchivo padre;
    /** Nombre del usuario dueño (por ejemplo admin o user1). */
    private final String dueno;
    /** Quién puede leerlo además de su dueño. */
    private Permiso permiso;
    /** Primer bloque de su cadena en el disco; -1 en un directorio. */
    private final int primerBloque;
    /** Cantidad de bloques que ocupa; 0 en un directorio. */
    private final int bloques;
    /** Etiqueta del proceso que lo creó, o un texto como "carga inicial". */
    private final String creador;
    /** Hijos, en el orden en que se crearon (vacío en un archivo). */
    private final ListaEnlazada<NodoArchivo> hijos = new ListaEnlazada<>();

    /** Crea el nodo; el SistemaArchivos es quien le da su id y su lugar en el árbol. */
    NodoArchivo(int id, String nombre, TipoNodo tipo, String dueno, Permiso permiso, int primerBloque, int bloques,
                String creador) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = tipo;
        this.dueno = dueno;
        this.permiso = permiso;
        this.primerBloque = primerBloque;
        this.bloques = bloques;
        this.creador = creador;
    }

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public TipoNodo getTipo() {
        return tipo;
    }

    public boolean esDirectorio() {
        return tipo == TipoNodo.DIRECTORIO;
    }

    public NodoArchivo getPadre() {
        return padre;
    }

    void setPadre(NodoArchivo padre) {
        this.padre = padre;
    }

    public String getDueno() {
        return dueno;
    }

    public Permiso getPermiso() {
        return permiso;
    }

    public int getPrimerBloque() {
        return primerBloque;
    }

    public int getBloques() {
        return bloques;
    }

    public String getCreador() {
        return creador;
    }

    ListaEnlazada<NodoArchivo> getHijos() {
        return hijos;
    }

    /** Ruta completa desde la raíz, por ejemplo /sistema/boot.bin; la raíz es "/". */
    public String getRuta() {
        if (padre == null) {
            return "/";
        }
        String rutaPadre = padre.getRuta();
        return (rutaPadre.equals("/") ? "" : rutaPadre) + "/" + nombre;
    }

    @Override
    public String toString() {
        return nombre.isEmpty() ? "/" : nombre;
    }
}

package avilaos.archivos;

import avilaos.json.Json;
import avilaos.json.JsonArreglo;
import avilaos.json.JsonObjeto;

/**
 * Lo que dice un JSON de sistema de archivos antes de crear nada: el tamaño del disco, la posición inicial del cabezal
 * y la lista de entradas. Primero se lee la definición para crear el Disco con su tamaño y después se cargan las
 * entradas en el SistemaArchivos que lo usa.
 */
public record DefinicionSistemaArchivos(int bloquesTotales, int cabezalInicial, JsonArreglo entradas) {

    /** Menor cantidad de bloques que se acepta para el disco. */
    public static final int MIN_BLOQUES = 10;
    /** Mayor cantidad de bloques que se acepta para el disco. */
    public static final int MAX_BLOQUES = 1000;

    /** Lee y valida el JSON: diskConfig.totalBlocks, diskConfig.initialHead y el arreglo fileSystem. */
    public static DefinicionSistemaArchivos desdeJson(String texto) {
        JsonObjeto raiz = Json.leerObjeto(texto);
        JsonObjeto disco = raiz.objeto("diskConfig");
        int total = disco.entero("totalBlocks");
        int cabezal = disco.entero("initialHead");
        if (total < MIN_BLOQUES || total > MAX_BLOQUES) {
            throw new IllegalArgumentException("totalBlocks debe estar entre " + MIN_BLOQUES + " y " + MAX_BLOQUES + ".");
        }
        if (cabezal < 0 || cabezal >= total) {
            throw new IllegalArgumentException("initialHead debe estar entre 0 y " + (total - 1) + ".");
        }
        return new DefinicionSistemaArchivos(total, cabezal, raiz.arreglo("fileSystem"));
    }
}

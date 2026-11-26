package avilaos.json;

import avilaos.estructuras.ListaEnlazada;

/** Un arreglo JSON: una lista de valores (JsonObjeto, JsonArreglo, String, Long, Double, Boolean o null) en orden. */
public final class JsonArreglo {

    /** Elementos del arreglo, en el orden en que aparecen. */
    private final ListaEnlazada<Object> elementos = new ListaEnlazada<>();

    /** Agrega un valor al final. */
    public void agregar(Object valor) {
        elementos.agregarAlFinal(valor);
    }

    public int tamanio() {
        return elementos.getTamanio();
    }

    /** El valor en la posición dada (desde 0). */
    public Object obtener(int indice) {
        if (indice < 0 || indice >= elementos.getTamanio()) {
            throw new IllegalArgumentException("El arreglo JSON no tiene la posición " + indice + ".");
        }
        return elementos.obtener(indice);
    }

    /** El elemento en la posición dada, que debe ser un objeto JSON. */
    public JsonObjeto objeto(int indice) {
        Object valor = obtener(indice);
        if (valor instanceof JsonObjeto objeto) {
            return objeto;
        }
        throw new IllegalArgumentException("El elemento " + (indice + 1) + " del arreglo debe ser un objeto { }.");
    }
}

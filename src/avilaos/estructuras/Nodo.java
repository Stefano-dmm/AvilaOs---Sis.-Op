package avilaos.estructuras;

/**
 * Nodo de una lista enlazada simple.
 * Guarda un dato y un puntero (siguiente) al nodo que le sigue; el ultimo nodo de la lista tiene
 * siguiente == null. No es seguro entre hilos: quien comparta una lista debe protegerla (ver el
 * mapa de concurrencia en docs/Guia_del_codigo.md).
 *
 * @param <T> tipo del dato guardado
 */
public class Nodo<T> {

    /** El dato que guarda este nodo. */
    private T dato;
    /** Puntero al nodo siguiente de la lista; null si este es el ultimo. */
    private Nodo<T> siguiente;

    /** Crea un nodo con el dato y sin siguiente. */
    public Nodo(T dato) {
        this.dato = dato;
        this.siguiente = null;
    }

    public T getDato() {
        return dato;
    }

    public void setDato(T dato) {
        this.dato = dato;
    }

    public Nodo<T> getSiguiente() {
        return siguiente;
    }

    public void setSiguiente(Nodo<T> siguiente) {
        this.siguiente = siguiente;
    }
}

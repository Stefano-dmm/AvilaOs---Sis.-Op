package avilaos.estructuras;

/**
 * Pila LIFO (ultimo en entrar, primero en salir) construida sobre una ListaEnlazada: la cima es la cabeza
 * de la lista. Es una de las estructuras propias que pide el enunciado; el nucleo actual no necesita una
 * pila (las colas bastan para los estados de los procesos), pero se conserva disponible para extensiones
 * como el Proyecto 2. No es segura entre hilos.
 *
 * @param <T> tipo de los elementos
 */
public class Pila<T> {

    /** Almacenamiento: la cima de la pila es la cabeza de la lista. */
    private final ListaEnlazada<T> lista = new ListaEnlazada<>();

    /** Pone un elemento en la cima. */
    public void apilar(T dato) {
        lista.agregarAlInicio(dato);
    }

    /** Quita y devuelve el elemento de la cima; null si la pila esta vacia. */
    public T desapilar() {
        return lista.eliminarAlInicio();
    }

    /** Devuelve la cima sin quitarla; null si esta vacia. */
    public T cima() {
        return lista.obtener(0);
    }

    /** Verdadero si no hay elementos. */
    public boolean estaVacia() {
        return lista.estaVacia();
    }

    /** Cantidad de elementos en la pila. */
    public int getTamanio() {
        return lista.getTamanio();
    }

    @Override
    public String toString() {
        return lista.toString();
    }
}

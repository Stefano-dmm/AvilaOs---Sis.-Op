package avilaos.estructuras;

/**
 * Pila LIFO genérica basada en lista enlazada propia.
 */
public class Pila<T> {

    private final ListaEnlazada<T> lista;

    public Pila() {
        this.lista = new ListaEnlazada<>();
    }

    public void push(T dato) {
        lista.agregarAlInicio(dato);
    }

    public T pop() {
        return lista.eliminarAlInicio();
    }

    public T peek() {
        if (lista.estaVacia()) {
            return null;
        }
        return lista.obtener(0);
    }

    public boolean estaVacia() {
        return lista.estaVacia();
    }

    public int getTamanio() {
        return lista.getTamanio();
    }

    @Override
    public String toString() {
        return lista.toString();
    }
}

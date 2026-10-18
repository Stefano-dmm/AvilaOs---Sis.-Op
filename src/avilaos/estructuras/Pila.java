package avilaos.estructuras;

public class Pila<T> {

    private final ListaEnlazada<T> lista = new ListaEnlazada<>();

    public void apilar(T dato) {
        lista.agregarAlInicio(dato);
    }

    public T desapilar() {
        return lista.eliminarAlInicio();
    }

    public T cima() {
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

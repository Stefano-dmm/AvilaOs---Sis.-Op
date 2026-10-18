package avilaos.estructuras;

public class Cola<T> {

    private final ListaEnlazada<T> lista = new ListaEnlazada<>();

    public void encolar(T dato) {
        lista.agregarAlFinal(dato);
    }

    public T desencolar() {
        return lista.eliminarAlInicio();
    }

    public T frente() {
        return lista.obtener(0);
    }

    public boolean estaVacia() {
        return lista.estaVacia();
    }

    public int getTamanio() {
        return lista.getTamanio();
    }

    public ListaEnlazada<T> getLista() {
        return lista;
    }

    @Override
    public String toString() {
        return lista.toString();
    }
}

package avilaos.estructuras;

/**
 * Cola FIFO (primero en entrar, primero en salir) construida sobre una ListaEnlazada: se encola al final
 * y se desencola del inicio. Se usa para las colas de nuevos, listos, bloqueados y terminados de cada
 * Computador, para la cola de espera de cada SemaforoSO y para la bandeja de despertares. No es segura
 * entre hilos; cada cola esta protegida por su dueno (ver docs/Guia_del_codigo.md).
 *
 * @param <T> tipo de los elementos
 */
public class Cola<T> {

    /** Almacenamiento: el frente de la cola es la cabeza de la lista y el final es su cola. */
    private final ListaEnlazada<T> lista = new ListaEnlazada<>();

    /** Agrega al final de la cola. */
    public void encolar(T dato) {
        lista.agregarAlFinal(dato);
    }

    /** Quita y devuelve el elemento del frente; null si la cola esta vacia. */
    public T desencolar() {
        return lista.eliminarAlInicio();
    }

    /** Devuelve el elemento del frente sin quitarlo; null si esta vacia. */
    public T frente() {
        return lista.obtener(0);
    }

    /** Verdadero si no hay elementos. */
    public boolean estaVacia() {
        return lista.estaVacia();
    }

    /** Cantidad de elementos en la cola. */
    public int getTamanio() {
        return lista.getTamanio();
    }

    /**
     * Acceso a la lista interna para recorrerla (sin sacar elementos) o quitar uno que no esta al frente,
     * por ejemplo cuando un proceso termina por deadline.
     */
    public ListaEnlazada<T> getLista() {
        return lista;
    }

    @Override
    public String toString() {
        return lista.toString();
    }
}

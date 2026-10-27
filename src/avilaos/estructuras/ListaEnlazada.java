package avilaos.estructuras;

/**
 * Lista enlazada simple propia, base de las colas, las pilas y las listas del sistema (no se usa
 * ninguna coleccion de java.util). Se recorre siguiendo los punteros desde la cabeza:
 * <pre>
 * cabeza -&gt; [dato|siguiente] -&gt; [dato|siguiente] -&gt; [dato|null]
 *                                                      ^
 *                                                     cola
 * </pre>
 * No es segura entre hilos. En AvilaOS cada lista tiene un unico "dueno" que la protege:
 * las colas de un Computador solo las toca el hilo de ese computador (o la interfaz con el cerrojo del
 * Cluster tomado), la cola de espera de un SemaforoSO esta protegida por el candado del semaforo, y
 * la lista de suscriptores del RelojGlobal por su candadoSuscriptores.
 *
 * @param <T> tipo de los elementos
 */
public class ListaEnlazada<T> {

    /** Puntero al primer nodo; null si la lista esta vacia. Aqui empiezan los recorridos y las extracciones. */
    private Nodo<T> cabeza;
    /** Puntero al ultimo nodo; permite agregar al final en O(1) sin recorrer la lista. Es null si esta vacia. */
    private Nodo<T> cola;
    /** Cantidad de nodos; se mantiene al agregar y quitar para que getTamanio sea O(1). */
    private int tamanio;

    /** Crea una lista vacia: cabeza y cola apuntan a null. */
    public ListaEnlazada() {
        this.cabeza = null;
        this.cola = null;
        this.tamanio = 0;
    }

    /** Verdadero si no tiene elementos. */
    public boolean estaVacia() {
        return tamanio == 0;
    }

    public int getTamanio() {
        return tamanio;
    }

    /** Devuelve el primer nodo para poder recorrer la lista con getSiguiente; null si esta vacia. */
    public Nodo<T> getCabeza() {
        return cabeza;
    }

    /** Agrega un dato al final en O(1): el nodo anterior (cola) apunta al nuevo y cola pasa a ser el nuevo. */
    public void agregarAlFinal(T dato) {
        Nodo<T> nuevo = new Nodo<>(dato);
        if (estaVacia()) {
            cabeza = nuevo;
            cola = nuevo;
        } else {
            cola.setSiguiente(nuevo);
            cola = nuevo;
        }
        tamanio++;
    }

    /** Agrega un dato al inicio en O(1): el nuevo nodo apunta a la cabeza anterior y pasa a ser la cabeza. */
    public void agregarAlInicio(T dato) {
        Nodo<T> nuevo = new Nodo<>(dato);
        nuevo.setSiguiente(cabeza);
        cabeza = nuevo;
        if (cola == null) {
            cola = nuevo;
        }
        tamanio++;
    }

    /** Quita y devuelve el primer dato en O(1); devuelve null si la lista esta vacia. */
    public T eliminarAlInicio() {
        if (estaVacia()) {
            return null;
        }
        T dato = cabeza.getDato();
        cabeza = cabeza.getSiguiente();
        if (cabeza == null) {
            cola = null;
        }
        tamanio--;
        return dato;
    }

    /**
     * Quita la primera aparicion del dato (comparando con equals) en O(n). Reconecta el nodo anterior con el
     * siguiente y, si quito el ultimo, mueve el puntero cola. Devuelve false si el dato no estaba.
     */
    public boolean eliminar(T dato) {
        if (estaVacia()) {
            return false;
        }

        if (mismoDato(cabeza.getDato(), dato)) {
            eliminarAlInicio();
            return true;
        }

        Nodo<T> actual = cabeza;
        while (actual.getSiguiente() != null) {
            if (mismoDato(actual.getSiguiente().getDato(), dato)) {
                Nodo<T> eliminado = actual.getSiguiente();
                actual.setSiguiente(eliminado.getSiguiente());
                if (eliminado == cola) {
                    cola = actual;
                }
                tamanio--;
                return true;
            }
            actual = actual.getSiguiente();
        }
        return false;
    }

    /** Busca el dato recorriendo la lista (O(n)) con equals. */
    public boolean contiene(T dato) {
        Nodo<T> actual = cabeza;
        while (actual != null) {
            if (mismoDato(actual.getDato(), dato)) {
                return true;
            }
            actual = actual.getSiguiente();
        }
        return false;
    }

    /** Devuelve el dato en la posicion indicada (desde 0) en O(n); null si el indice esta fuera de rango. */
    public T obtener(int indice) {
        if (indice < 0 || indice >= tamanio) {
            return null;
        }
        Nodo<T> actual = cabeza;
        for (int i = 0; i < indice; i++) {
            actual = actual.getSiguiente();
        }
        return actual.getDato();
    }

    /** Compara dos datos con equals, aceptando nulls. */
    private boolean mismoDato(T a, T b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    @Override
    public String toString() {
        if (estaVacia()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        Nodo<T> actual = cabeza;
        while (actual != null) {
            sb.append(actual.getDato());
            if (actual.getSiguiente() != null) {
                sb.append(" -> ");
            }
            actual = actual.getSiguiente();
        }
        sb.append("]");
        return sb.toString();
    }
}

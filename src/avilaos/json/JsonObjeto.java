package avilaos.json;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;

/**
 * Un objeto JSON: pares clave-valor en el orden en que se escribieron. Los métodos de lectura validan el tipo y
 * lanzan IllegalArgumentException con un mensaje que nombra el campo, para que la interfaz pueda mostrarlo.
 */
public final class JsonObjeto {

    /** Un par clave-valor del objeto. */
    private record Miembro(String clave, Object valor) {
    }

    /** Pares del objeto, en orden de escritura. */
    private final ListaEnlazada<Miembro> miembros = new ListaEnlazada<>();

    /** Agrega un par; si la clave ya existe se reemplaza su valor. */
    public JsonObjeto poner(String clave, Object valor) {
        Miembro existente = buscar(clave);
        if (existente != null) {
            miembros.eliminar(existente);
        }
        miembros.agregarAlFinal(new Miembro(clave, valor));
        return this;
    }

    private Miembro buscar(String clave) {
        Nodo<Miembro> actual = miembros.getCabeza();
        while (actual != null) {
            if (actual.getDato().clave().equals(clave)) {
                return actual.getDato();
            }
            actual = actual.getSiguiente();
        }
        return null;
    }

    public boolean tiene(String clave) {
        return buscar(clave) != null;
    }

    /** Valor crudo de la clave; null si no existe o vale null. */
    public Object obtener(String clave) {
        Miembro m = buscar(clave);
        return m == null ? null : m.valor();
    }

    /** Cantidad de pares. */
    public int tamanio() {
        return miembros.getTamanio();
    }

    /** Clave del par en la posición dada (desde 0), para recorrer el objeto al escribirlo. */
    String claveEn(int indice) {
        return miembros.obtener(indice).clave();
    }

    /** Valor del par en la posición dada (desde 0). */
    Object valorEn(int indice) {
        return miembros.obtener(indice).valor();
    }

    /** Texto obligatorio. */
    public String texto(String clave) {
        Object valor = obtener(clave);
        if (valor == null) {
            throw new IllegalArgumentException("Falta el campo '" + clave + "'.");
        }
        if (!(valor instanceof String texto)) {
            throw new IllegalArgumentException("El campo '" + clave + "' debe ser un texto.");
        }
        return texto;
    }

    /** Texto opcional: devuelve defecto si el campo no está. */
    public String texto(String clave, String defecto) {
        return tiene(clave) && obtener(clave) != null ? texto(clave) : defecto;
    }

    /** Entero obligatorio: rechaza textos, decimales y valores fuera del rango de int. */
    public int entero(String clave) {
        Object valor = obtener(clave);
        if (valor == null) {
            throw new IllegalArgumentException("Falta el campo '" + clave + "'.");
        }
        if (!(valor instanceof Long numero) || numero > Integer.MAX_VALUE || numero < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("El campo '" + clave + "' debe ser un número entero.");
        }
        return (int) (long) numero;
    }

    /** Entero opcional: devuelve defecto si el campo no está. */
    public int entero(String clave, int defecto) {
        return tiene(clave) && obtener(clave) != null ? entero(clave) : defecto;
    }

    /** Subobjeto obligatorio. */
    public JsonObjeto objeto(String clave) {
        Object valor = obtener(clave);
        if (valor == null) {
            throw new IllegalArgumentException("Falta el objeto '" + clave + "'.");
        }
        if (!(valor instanceof JsonObjeto objeto)) {
            throw new IllegalArgumentException("El campo '" + clave + "' debe ser un objeto { }.");
        }
        return objeto;
    }

    /** Arreglo obligatorio. */
    public JsonArreglo arreglo(String clave) {
        Object valor = obtener(clave);
        if (valor == null) {
            throw new IllegalArgumentException("Falta el arreglo '" + clave + "'.");
        }
        if (!(valor instanceof JsonArreglo arreglo)) {
            throw new IllegalArgumentException("El campo '" + clave + "' debe ser un arreglo [ ].");
        }
        return arreglo;
    }

    /** Arreglo opcional; null si el campo no está. */
    public JsonArreglo arregloOpcional(String clave) {
        return tiene(clave) && obtener(clave) != null ? arreglo(clave) : null;
    }
}

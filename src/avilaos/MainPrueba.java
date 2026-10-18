package avilaos;

import avilaos.estructuras.Cola;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Pila;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;

/**
 * Prueba rápida de la primera parte: enums, PCB y estructuras propias.
 */
public class MainPrueba {

    public static void main(String[] args) {
        System.out.println("=== AvilaOS - Parte 1: estructuras y PCB ===\n");

        ListaEnlazada<String> lista = new ListaEnlazada<>();
        lista.agregarAlFinal("A");
        lista.agregarAlFinal("B");
        lista.agregarAlFinal("C");
        System.out.println("Lista: " + lista);

        Cola<Proceso> listos = new Cola<>();
        Proceso p1 = new Proceso("Editor", TipoProceso.CPU_BOUND, 2, 64, 20, 10);
        Proceso p2 = new Proceso("Logger", TipoProceso.IO_BOUND, 1, 32, 15, 8);
        p1.setEstado(EstadoProceso.LISTO);
        p2.setEstado(EstadoProceso.LISTO);
        listos.encolar(p1);
        listos.encolar(p2);
        System.out.println("Cola de listos: " + listos);
        System.out.println("Desencolado: " + listos.desencolar());
        System.out.println("Cola tras desencolar: " + listos);

        Pila<Integer> pila = new Pila<>();
        pila.push(10);
        pila.push(20);
        pila.push(30);
        System.out.println("Pila: " + pila);
        System.out.println("Pop: " + pila.pop());
        System.out.println("Pila tras pop: " + pila);

        System.out.println("\nIDs unicos: P" + p1.getId() + " y P" + p2.getId());
        System.out.println("Parte 1 OK.");
    }
}

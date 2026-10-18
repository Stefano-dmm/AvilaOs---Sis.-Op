package avilaos;

import avilaos.estructuras.Cola;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Pila;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.Proceso;
import avilaos.modelo.TipoProceso;

public class MainPrueba {

    public static void main(String[] args) {
        Proceso.reiniciarContadorGlobal();

        System.out.println("=== AvilaOS - Parte 1 ===\n");

        ListaEnlazada<String> lista = new ListaEnlazada<>();
        lista.agregarAlFinal("A");
        lista.agregarAlFinal("B");
        lista.agregarAlFinal("C");
        System.out.println("Lista:              " + lista);

        Cola<Proceso> listos = new Cola<>();
        Proceso p1 = new Proceso("Editor", TipoProceso.CPU_BOUND, 2, 64, 20, 10);
        Proceso p2 = new Proceso("Logger", TipoProceso.IO_BOUND, 1, 32, 15, 8);
        p1.setEstado(EstadoProceso.LISTO);
        p2.setEstado(EstadoProceso.LISTO);
        listos.encolar(p1);
        listos.encolar(p2);

        System.out.println("Cola de listos:     " + listos);
        System.out.println("Desencolado:        " + listos.desencolar().getEtiqueta());
        System.out.println("Frente ahora:       " + listos.frente().getEtiqueta());

        Pila<Integer> pila = new Pila<>();
        pila.apilar(10);
        pila.apilar(20);
        pila.apilar(30);
        System.out.println("Pila:               " + pila);
        System.out.println("Desapilado:         " + pila.desapilar());
        System.out.println("Cima ahora:         " + pila.cima());

        p2.ejecutarUnCiclo();
        System.out.println("\nTras 1 ciclo:       " + p2);
        System.out.println("IDs unicos:         " + p1.getEtiqueta() + ", " + p2.getEtiqueta());
        System.out.println("\nParte 1 OK.");
    }
}

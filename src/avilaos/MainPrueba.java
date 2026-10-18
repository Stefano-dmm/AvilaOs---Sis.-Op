package avilaos;

import avilaos.nucleo.Computador;

public class MainPrueba {

    public static void main(String[] args) {
        Computador.reiniciarContadorIds();

        System.out.println("=== AvilaOS - Computador ===\n");

        Computador pc1 = new Computador("Nodo-A", 1024, 4);
        Computador pc2 = new Computador("Nodo-B", 2048, 4);

        System.out.println(pc1);
        System.out.println(pc2);
        System.out.println("\nRAM libre PC1: " + pc1.getRamLibre());
        System.out.println("RAM libre PC2: " + pc2.getRamLibre());

        pc1.reservarMemoria(256);
        System.out.println("\nTras reservar 256 en PC1:");
        System.out.println(pc1);

        pc1.liberarMemoria(256);
        System.out.println("Tras liberar 256 en PC1:");
        System.out.println(pc1);

        System.out.println("\nComputador OK.");
    }
}

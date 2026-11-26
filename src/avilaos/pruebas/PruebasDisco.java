package avilaos.pruebas;

import static avilaos.pruebas.Verificador.casi;
import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.lanza;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.disco.DireccionCabezal;
import avilaos.disco.Disco;
import avilaos.disco.MapaBloques;
import avilaos.disco.OperacionES;
import avilaos.disco.PoliticaDisco;
import avilaos.disco.SolicitudES;

/**
 * El disco aislado, con los casos de prueba del enunciado: las cuatro políticas con el cabezal en 50 (casos P1 a P4),
 * el ejemplo del anexo con sus desplazamientos totales, la velocidad del cabezal, la asignación encadenada y las métricas.
 */
public final class PruebasDisco {

    private PruebasDisco() {
    }

    /** Resultado de atender una cola completa: el orden de atención y el desplazamiento total del cabezal. */
    private record Corrida(String orden, int desplazamiento, int ciclos) {
    }

    /** Encola los bloques en orden de llegada y avanza el reloj del disco hasta que quede libre. */
    private static Corrida atender(PoliticaDisco politica, int cabezal, int[] bloques) {
        Disco disco = new Disco(200, 0, cabezal, 1000, politica);
        StringBuilder orden = new StringBuilder();
        int[] ultimo = {0};
        disco.setOyente((s, ciclo) -> {
            orden.append(orden.length() == 0 ? "" : ",").append(s.getBloque());
            ultimo[0] = ciclo;
        });
        for (int b : bloques) {
            disco.encolar(new SolicitudES(OperacionES.LEER, b, 1, 1, "P1-x", "/x", 1));
        }
        int ciclo = 1;
        while ((disco.getTamanioCola() > 0 || disco.getEnServicio() != null) && ciclo < 5000) {
            disco.tic(ciclo++);
        }
        return new Corrida(orden.toString(), disco.getDesplazamientoTotal(), ultimo[0]);
    }

    public static void ejecutar() {
        casosDelEnunciado();
        ejemploDelAnexo();
        velocidadDelCabezal();
        mapaEncadenado();
        reservasEnElDisco();
        metricasYCambioDePolitica();
    }

    private static void casosDelEnunciado() {
        seccion("Disco: casos de prueba P1 a P4 del enunciado (cabezal en 50)");
        int[] cola = {95, 180, 34, 119, 11, 123, 62, 64};
        igual("95,180,34,119,11,123,62,64", atender(PoliticaDisco.FIFO, 50, cola).orden(), "P1 FIFO atiende en orden de llegada");
        igual("62,64,34,11,95,119,123,180", atender(PoliticaDisco.SSTF, 50, cola).orden(), "P2 SSTF atiende siempre la más cercana");
        igual("62,64,95,119,123,180,34,11", atender(PoliticaDisco.SCAN, 50, cola).orden(), "P3 SCAN sube hasta el 199 y regresa por 34 y 11");
        igual("62,64,95,119,123,180,11,34", atender(PoliticaDisco.C_SCAN, 50, cola).orden(), "P4 C-SCAN salta al 0 y sube por 11 y 34");
    }

    private static void ejemploDelAnexo() {
        seccion("Disco: ejemplo del anexo (cola 82, 17, 43, 140, 24, 16, 190)");
        int[] cola = {82, 17, 43, 140, 24, 16, 190};
        Corrida fifo = atender(PoliticaDisco.FIFO, 50, cola);
        igual("82,17,43,140,24,16,190", fifo.orden(), "FIFO: orden del anexo");
        igual(518, fifo.desplazamiento(), "FIFO: 518 bloques de desplazamiento");
        Corrida sstf = atender(PoliticaDisco.SSTF, 50, cola);
        igual("43,24,17,16,82,140,190", sstf.orden(), "SSTF: orden del anexo");
        igual(208, sstf.desplazamiento(), "SSTF: 208 bloques de desplazamiento");
        Corrida scan = atender(PoliticaDisco.SCAN, 50, cola);
        igual("82,140,190,43,24,17,16", scan.orden(), "SCAN: orden del anexo (llega al 199 y regresa)");
        igual(332, scan.desplazamiento(), "SCAN: 332 bloques de desplazamiento");
        Corrida cscan = atender(PoliticaDisco.C_SCAN, 50, cola);
        igual("82,140,190,16,17,24,43", cscan.orden(), "C-SCAN: orden del anexo (llega al 199 y salta al 0)");
        igual(192, cscan.desplazamiento(), "C-SCAN: 192 bloques sin contar el salto de regreso");
    }

    private static void velocidadDelCabezal() {
        seccion("Disco: velocidad del cabezal y un ciclo por bloque transferido");
        Disco disco = new Disco(200, 0, 50, 10, PoliticaDisco.FIFO);
        int[] fin = {0};
        disco.setOyente((s, ciclo) -> fin[0] = ciclo);
        disco.encolar(new SolicitudES(OperacionES.LEER, 95, 3, 1, "P1-x", "/x", 1));
        for (int ciclo = 1; ciclo <= 20; ciclo++) {
            disco.tic(ciclo);
        }
        igual(8, fin[0], "45 posiciones a 10 por ciclo son 5 ciclos de viaje más 3 bloques leídos: termina en el ciclo 8");
        igual(95, disco.getPosicion(), "el cabezal queda en el bloque pedido");
        igual(DireccionCabezal.SUBE, disco.getDireccion(), "y su dirección es hacia los bloques mayores");

        Disco local = new Disco(200, 0, 50, 10, PoliticaDisco.FIFO);
        int[] finLocal = {0};
        local.setOyente((s, ciclo) -> finLocal[0] = ciclo);
        local.encolar(new SolicitudES(OperacionES.LEER, 50, 2, 1, "P1-x", "/x", 1));
        for (int ciclo = 1; ciclo <= 5; ciclo++) {
            local.tic(ciclo);
        }
        igual(2, finLocal[0], "si el cabezal ya está en el bloque solo cuesta transferir sus 2 bloques");
    }

    private static void mapaEncadenado() {
        seccion("Disco: asignación encadenada de bloques");
        MapaBloques mapa = new MapaBloques(10);
        int a = mapa.asignar(3, 1, 0, 10);
        int b = mapa.asignar(3, 2, 0, 10);
        igual(0, a, "el primer archivo empieza en el bloque 0");
        igual(3, b, "el segundo empieza en el bloque 3");
        igual(1, mapa.getSiguiente(0), "cada bloque apunta al siguiente de su archivo");
        igual(MapaBloques.FIN_DE_CADENA, mapa.getSiguiente(2), "el último bloque no tiene siguiente");
        igual(3, mapa.liberar(a), "al eliminar el primer archivo se liberan sus 3 bloques");
        igual(7, mapa.getLibres(), "quedan 7 bloques libres");
        int c = mapa.asignar(5, 3, 0, 10);
        igual(0, c, "un archivo nuevo reutiliza los bloques liberados");
        igual(5, mapa.contarCadena(c), "su cadena tiene 5 bloques aunque no sean contiguos");
        igual(4, mapa.getSiguiente(3), "el otro archivo conserva su cadena intacta");
        igual(-1, mapa.asignar(3, 4, 0, 10), "no se asigna si no hay bloques libres suficientes");
        verificar(!mapa.asignarContiguos(8, 5, 5), "los bloques contiguos no pueden salirse del disco");
        lanza(IllegalArgumentException.class, () -> mapa.asignar(0, 1, 0, 10), "no se puede asignar cero bloques");
        lanza(IllegalArgumentException.class, () -> new MapaBloques(0), "el disco necesita al menos un bloque");
    }

    private static void reservasEnElDisco() {
        seccion("Disco: área de archivos y área de intercambio");
        Disco disco = new Disco(20, 10, 0, 5, PoliticaDisco.FIFO);
        igual(30, disco.getTotalBloques(), "el disco suma el área de archivos y el swap");
        int archivo = disco.reservarBloquesArchivo(5, 1);
        igual(0, archivo, "los archivos se asignan desde el bloque 0");
        int swap = disco.reservarBloquesSwap(4, 7);
        igual(20, swap, "el swap empieza después del último bloque de archivos");
        igual(-7, disco.getDueno(20), "el dueño de un bloque de swap es el proceso suspendido");
        igual(6, disco.getBloquesLibresSwap(), "quedan 6 bloques libres de swap");
        igual(15, disco.getBloquesLibresArchivos(), "y 15 de archivos");
        igual(-1, disco.reservarBloquesSwap(7, 8), "no se suspende un proceso que no cabe en el swap");
        igual(4, disco.liberarBloques(swap), "al reactivar el proceso se libera su swap");
        igual(-1, disco.reservarBloquesArchivo(16, 2), "un archivo no puede usar el área de swap");
        verificar(!disco.reservarBloquesDesde(18, 5, 3), "la carga desde un bloque no puede invadir el swap");
        lanza(IllegalArgumentException.class, () -> new Disco(0, 0, 0, 1, PoliticaDisco.FIFO), "no se crea un disco sin bloques");
        lanza(IllegalArgumentException.class, () -> new Disco(10, 0, 10, 1, PoliticaDisco.FIFO), "el cabezal no puede empezar fuera del disco");
        lanza(IllegalArgumentException.class, () -> new Disco(10, 0, 0, 0, PoliticaDisco.FIFO), "el cabezal debe moverse al menos una posición por ciclo");
        lanza(IllegalArgumentException.class, () -> disco.encolar(new SolicitudES(OperacionES.LEER, 99, 1, 1, "P1-x", null, 1)),
                "no se encola una solicitud a un bloque que no existe");
    }

    private static void metricasYCambioDePolitica() {
        seccion("Disco: métricas y cambio de política en ejecución");
        Disco disco = new Disco(200, 0, 50, 1000, PoliticaDisco.FIFO);
        disco.encolar(new SolicitudES(OperacionES.LEER, 60, 1, 1, "P1-a", null, 1));
        disco.encolar(new SolicitudES(OperacionES.LEER, 70, 1, 2, "P2-b", null, 1));
        for (int ciclo = 1; ciclo <= 4; ciclo++) {
            disco.tic(ciclo);
        }
        igual(2, disco.getAtendidas(), "se atendieron las dos solicitudes");
        igual(20, disco.getDesplazamientoTotal(), "el cabezal recorrió 10 posiciones hasta cada bloque");
        casi(1.0, disco.getEsperaPromedio(), "la espera promedio es (0 + 2) / 2 ciclos");

        disco.setPolitica(PoliticaDisco.SSTF);
        disco.encolar(new SolicitudES(OperacionES.LEER, 75, 1, 3, "P3-c", null, 5));
        for (int ciclo = 5; ciclo <= 8; ciclo++) {
            disco.tic(ciclo);
        }
        igual(PoliticaDisco.SSTF, disco.getNombrePolitica(), "la política cambió mientras el sistema corría");
        igual(2, disco.getAtendidas(PoliticaDisco.FIFO), "dos solicitudes se atendieron con FIFO");
        igual(1, disco.getAtendidas(PoliticaDisco.SSTF), "y una con SSTF");
        igual(8, disco.copiarSerie().getTamanio(), "la serie del gráfico tiene una muestra por ciclo");
        igual(25, disco.copiarSerie().getAcumulado(7), "el acumulado de la última muestra es el desplazamiento total");
    }
}

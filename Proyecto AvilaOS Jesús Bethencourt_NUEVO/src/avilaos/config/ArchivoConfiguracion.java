package avilaos.config;

import avilaos.estructuras.Nodo;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.TipoProceso;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lee y escribe la configuracion en un CSV sencillo, una linea por elemento:
 * CICLO_MS,500 | LATENCIA_RED,2 | COMPUTADOR,nombre,ram,politica,quantum
 * | PROCESO,nombre,tipo,prioridad,memoria,deadline,instrucciones,indiceComputador
 */
public final class ArchivoConfiguracion {

    private ArchivoConfiguracion() {
    }

    public static void guardar(Path ruta, Configuracion config) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(ruta, StandardCharsets.UTF_8)) {
            out.write("# Configuracion de AvilaOS");
            out.newLine();
            out.write("CICLO_MS," + config.getCicloMs());
            out.newLine();
            out.write("LATENCIA_RED," + config.getLatenciaRed());
            out.newLine();

            Nodo<ConfigComputador> pc = config.getComputadores().getCabeza();
            while (pc != null) {
                ConfigComputador c = pc.getDato();
                out.write("COMPUTADOR," + c.nombre() + "," + c.ram() + "," + c.politica() + "," + c.quantum());
                out.newLine();
                pc = pc.getSiguiente();
            }

            Nodo<ConfigProceso> pr = config.getProcesos().getCabeza();
            while (pr != null) {
                ConfigProceso p = pr.getDato();
                out.write("PROCESO," + p.nombre() + "," + p.tipo() + "," + p.prioridad() + ","
                        + p.memoria() + "," + p.deadline() + "," + p.instrucciones() + ","
                        + p.indiceComputador());
                out.newLine();
                pr = pr.getSiguiente();
            }
        }
    }

    /** Lanza IllegalArgumentException indicando la linea cuando el archivo tiene datos invalidos. */
    public static Configuracion cargar(Path ruta) throws IOException {
        Configuracion config = new Configuracion();
        try (BufferedReader in = Files.newBufferedReader(ruta, StandardCharsets.UTF_8)) {
            int numero = 0;
            String linea;
            while ((linea = in.readLine()) != null) {
                numero++;
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) {
                    continue;
                }
                try {
                    procesarLinea(config, linea.split(",", -1));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Linea " + numero + ": " + ex.getMessage(), ex);
                }
            }
        }
        config.validarCompleta();
        return config;
    }

    private static void procesarLinea(Configuracion config, String[] campos) {
        switch (campos[0].trim()) {
            case "CICLO_MS" -> config.setCicloMs(entero(campos, 1, "duracion del ciclo", 2));
            case "LATENCIA_RED" -> config.setLatenciaRed(entero(campos, 1, "latencia de red", 2));
            case "COMPUTADOR" -> {
                exigirCampos(campos, 5);
                config.agregarComputador(new ConfigComputador(campos[1].trim(),
                        entero(campos, 2, "RAM", 5),
                        PoliticaPlanificacion.valueOf(campos[3].trim()),
                        entero(campos, 4, "quantum", 5)));
            }
            case "PROCESO" -> {
                exigirCampos(campos, 8);
                config.agregarProceso(new ConfigProceso(campos[1].trim(),
                        TipoProceso.valueOf(campos[2].trim()),
                        entero(campos, 3, "prioridad", 8),
                        entero(campos, 4, "memoria", 8),
                        entero(campos, 5, "deadline", 8),
                        entero(campos, 6, "instrucciones", 8),
                        entero(campos, 7, "indice de computador", 8)));
            }
            default -> throw new IllegalArgumentException("tipo de linea desconocido: " + campos[0]);
        }
    }

    private static void exigirCampos(String[] campos, int cantidad) {
        if (campos.length != cantidad) {
            throw new IllegalArgumentException("se esperaban " + cantidad + " campos y hay " + campos.length);
        }
    }

    private static int entero(String[] campos, int posicion, String nombre, int esperados) {
        exigirCampos(campos, esperados);
        try {
            return Integer.parseInt(campos[posicion].trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(nombre + " debe ser un numero entero");
        }
    }
}

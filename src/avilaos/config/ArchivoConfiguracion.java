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
 * <pre>
 * CICLO_MS,500
 * LATENCIA_RED,2
 * COMPUTADOR,nombre,ram,politica,quantum
 * BUFFER,nombre,capacidad,indiceComputador
 * PROCESO,nombre,tipo,prioridad,memoria,deadline,instrucciones,indiceComputador,indiceBuffer,intervalo,elementos,ciclosES
 * </pre>
 * Los indices empiezan en 0 y siguen el orden del archivo; indiceComputador -1 pide asignacion
 * automatica. Una linea PROCESO con solo 8 campos (formato anterior) se acepta como CPU bound.
 */
public final class ArchivoConfiguracion {

    /** Clase de utilidades: no se instancia. */
    private ArchivoConfiguracion() {
    }

    /** Escribe la configuracion en el CSV: parametros generales, computadores, buffers y procesos, en ese orden (los indices dependen del orden). */
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

            Nodo<ConfigBuffer> bf = config.getBuffers().getCabeza();
            while (bf != null) {
                ConfigBuffer b = bf.getDato();
                out.write("BUFFER," + b.nombre() + "," + b.capacidad() + "," + b.indiceComputador());
                out.newLine();
                bf = bf.getSiguiente();
            }

            Nodo<ConfigProceso> pr = config.getProcesos().getCabeza();
            while (pr != null) {
                ConfigProceso p = pr.getDato();
                out.write("PROCESO," + p.nombre() + "," + p.tipo() + "," + p.prioridad() + ","
                        + p.memoria() + "," + p.deadline() + "," + p.instrucciones() + ","
                        + p.indiceComputador() + "," + p.indiceBuffer() + "," + p.intervalo() + ","
                        + p.elementos() + "," + p.ciclosES());
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

    /** Interpreta una linea del CSV segun su primer campo (CICLO_MS, LATENCIA_RED, COMPUTADOR, BUFFER o PROCESO) y la agrega a la configuracion. */
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
            case "BUFFER" -> {
                exigirCampos(campos, 4);
                config.agregarBuffer(new ConfigBuffer(campos[1].trim(),
                        entero(campos, 2, "capacidad", 4),
                        entero(campos, 3, "indice de computador", 4)));
            }
            case "PROCESO" -> procesarProceso(config, campos);
            default -> throw new IllegalArgumentException("tipo de linea desconocido: " + campos[0]);
        }
    }

    /** Lee una linea PROCESO de 12 campos, o de 8 (formato anterior, que se carga como CPU bound). */
    private static void procesarProceso(Configuracion config, String[] campos) {
        if (campos.length != 8 && campos.length != 12) {
            throw new IllegalArgumentException("se esperaban 8 o 12 campos y hay " + campos.length);
        }
        int total = campos.length;
        TipoProceso tipo = TipoProceso.valueOf(campos[2].trim());
        boolean completo = total == 12;
        config.agregarProceso(new ConfigProceso(campos[1].trim(), tipo,
                entero(campos, 3, "prioridad", total),
                entero(campos, 4, "memoria", total),
                entero(campos, 5, "deadline", total),
                entero(campos, 6, "instrucciones", total),
                entero(campos, 7, "indice de computador", total),
                completo ? entero(campos, 8, "indice de buffer", total) : -1,
                completo ? entero(campos, 9, "intervalo", total) : 1,
                completo ? entero(campos, 10, "elementos", total) : 0,
                completo ? entero(campos, 11, "ciclos de E/S", total) : 0));
    }

    /** Comprueba que la linea tenga la cantidad de campos esperada. */
    private static void exigirCampos(String[] campos, int cantidad) {
        if (campos.length != cantidad) {
            throw new IllegalArgumentException("se esperaban " + cantidad + " campos y hay " + campos.length);
        }
    }

    /** Convierte un campo a entero con un mensaje claro si no lo es. */
    private static int entero(String[] campos, int posicion, String nombre, int esperados) {
        exigirCampos(campos, esperados);
        try {
            return Integer.parseInt(campos[posicion].trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(nombre + " debe ser un numero entero");
        }
    }
}

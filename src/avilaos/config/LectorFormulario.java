package avilaos.config;

import avilaos.modelo.TipoProceso;
import avilaos.nucleo.programas.ProgramaProductorConsumidor;

/**
 * Convierte el texto de los campos de un formulario en datos validados. Vive fuera de la interfaz
 * grafica para que las validaciones de tipo y rango se puedan probar sin abrir ventanas; los
 * mensajes de error son los que ve el usuario.
 */
public final class LectorFormulario {

    /** Clase de utilidades: no se instancia. */
    private LectorFormulario() {
    }

    /** Convierte el texto de un campo en un entero dentro del rango; el mensaje de error nombra el campo y explica el problema. */
    public static int entero(String texto, String campo, int min, int max) {
        String limpio = (texto == null) ? "" : texto.trim();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException(campo + " no puede estar vacio.");
        }
        int valor;
        try {
            valor = Integer.parseInt(limpio);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(campo + " debe ser un numero entero.");
        }
        Configuracion.validarRango(campo, valor, min, max);
        return valor;
    }

    /** Valida un nombre: no vacio y sin comas (el CSV las usa como separador). */
    public static String nombre(String texto, String campo) {
        String limpio = (texto == null) ? "" : texto.trim();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException(campo + " no puede estar vacio.");
        }
        if (limpio.contains(",")) {
            throw new IllegalArgumentException(campo + " no puede contener comas.");
        }
        return limpio;
    }

    /** Arma los datos de un buffer a partir del formulario, validando nombre, capacidad y anfitrion. */
    public static ConfigBuffer buffer(String nombre, String capacidad, int indiceComputador) {
        if (indiceComputador < 0) {
            throw new IllegalArgumentException("Debe elegir el computador anfitrion del buffer.");
        }
        return new ConfigBuffer(nombre(nombre, "El nombre del buffer"),
                entero(capacidad, "La capacidad", 1, Configuracion.MAX_CAPACIDAD_BUFFER), indiceComputador);
    }

    /**
     * Arma un proceso leyendo solo los campos que corresponden a su tipo.
     *
     * @param indiceComputador -1 para que AvilaOS lo asigne por balanceo
     * @param indiceBuffer posicion del buffer elegido (productor y consumidor), -1 si no hay
     * @param intervalo ciclos entre E/S (I/O bound) o ciclos por elemento (productor y consumidor)
     */
    public static ConfigProceso proceso(String nombre, TipoProceso tipo, String prioridad, String memoria,
                                        String deadline, String instrucciones, String intervalo,
                                        String elementos, String ciclosES, int indiceComputador,
                                        int indiceBuffer) {
        String nombreValido = nombre(nombre, "El nombre del proceso");
        if (tipo == null) {
            throw new IllegalArgumentException("Debe elegir el tipo del proceso.");
        }
        int prio = entero(prioridad, "La prioridad", 1, 100);
        int mem = entero(memoria, "La memoria", 1, Configuracion.MAX_RAM);
        int dl = entero(deadline, "El deadline", 1, 1000000);

        switch (tipo) {
            case CPU_BOUND -> {
                int instr = entero(instrucciones, "Las instrucciones", 1, Configuracion.MAX_INSTRUCCIONES);
                return new ConfigProceso(nombreValido, tipo, prio, mem, dl, instr, indiceComputador);
            }
            case IO_BOUND -> {
                int instr = entero(instrucciones, "Las instrucciones", 1, Configuracion.MAX_INSTRUCCIONES);
                int entre = entero(intervalo, "Los ciclos de CPU entre E/S", 1, Configuracion.MAX_INTERVALO);
                int duracion = entero(ciclosES, "Los ciclos de cada E/S", 1, Configuracion.MAX_INTERVALO);
                return new ConfigProceso(nombreValido, tipo, prio, mem, dl, instr, indiceComputador,
                        -1, entre, 0, duracion);
            }
            default -> {
                if (indiceBuffer < 0) {
                    throw new IllegalArgumentException(
                            "Debe elegir un buffer (primero cree uno en la pestana Buffers).");
                }
                int ciclos = entero(intervalo, "Los ciclos por elemento", 1, Configuracion.MAX_INTERVALO);
                int cantidad = entero(elementos, "Los elementos", 1, Configuracion.MAX_ELEMENTOS);
                int instr = ProgramaProductorConsumidor.instruccionesTotales(ciclos, cantidad);
                return new ConfigProceso(nombreValido, tipo, prio, mem, dl, instr, indiceComputador,
                        indiceBuffer, ciclos, cantidad, 0);
            }
        }
    }
}

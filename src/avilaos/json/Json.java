package avilaos.json;

/**
 * Lector y escritor de JSON hechos a mano (el proyecto no usa librerías externas). Lee objetos, arreglos, textos,
 * números, booleanos y null; los enteros salen como Long y el resto de los números como Double. Un JSON mal formado
 * lanza IllegalArgumentException indicando la posición del error.
 */
public final class Json {

    /** Profundidad máxima de anidamiento que se acepta al leer, para no desbordar la pila con un archivo malicioso. */
    private static final int PROFUNDIDAD_MAXIMA = 64;

    /** Texto que se está leyendo. */
    private final String texto;
    /** Posición del siguiente carácter por leer. */
    private int pos;

    private Json(String texto) {
        this.texto = texto;
    }

    /** Convierte el texto JSON en JsonObjeto, JsonArreglo, String, Long, Double, Boolean o null. */
    public static Object leer(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException("El JSON está vacío.");
        }
        String limpio = texto.startsWith("﻿") ? texto.substring(1) : texto;
        Json lector = new Json(limpio);
        lector.saltarEspacios();
        Object valor = lector.valor(0);
        lector.saltarEspacios();
        if (lector.pos < limpio.length()) {
            throw lector.error("hay texto después del final del JSON");
        }
        return valor;
    }

    /** Convierte el texto en un objeto JSON; falla si la raíz es otra cosa. */
    public static JsonObjeto leerObjeto(String texto) {
        Object valor = leer(texto);
        if (valor instanceof JsonObjeto objeto) {
            return objeto;
        }
        throw new IllegalArgumentException("El JSON debe empezar con un objeto { }.");
    }

    // ---- lectura ----

    private IllegalArgumentException error(String motivo) {
        return new IllegalArgumentException("JSON inválido en la posición " + pos + ": " + motivo + ".");
    }

    private void saltarEspacios() {
        while (pos < texto.length() && Character.isWhitespace(texto.charAt(pos))) {
            pos++;
        }
    }

    private char actual() {
        if (pos >= texto.length()) {
            throw error("el texto terminó antes de tiempo");
        }
        return texto.charAt(pos);
    }

    private void esperar(char esperado) {
        if (actual() != esperado) {
            throw error("se esperaba '" + esperado + "' y hay '" + actual() + "'");
        }
        pos++;
    }

    private Object valor(int profundidad) {
        if (profundidad > PROFUNDIDAD_MAXIMA) {
            throw error("demasiados niveles anidados");
        }
        char c = actual();
        if (c == '{') {
            return objeto(profundidad);
        }
        if (c == '[') {
            return arreglo(profundidad);
        }
        if (c == '"') {
            return cadena();
        }
        if (c == '-' || (c >= '0' && c <= '9')) {
            return numero();
        }
        if (texto.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (texto.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        if (texto.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw error("carácter inesperado '" + c + "'");
    }

    private JsonObjeto objeto(int profundidad) {
        JsonObjeto objeto = new JsonObjeto();
        esperar('{');
        saltarEspacios();
        if (actual() == '}') {
            pos++;
            return objeto;
        }
        while (true) {
            saltarEspacios();
            if (actual() != '"') {
                throw error("se esperaba el nombre de un campo entre comillas");
            }
            String clave = cadena();
            saltarEspacios();
            esperar(':');
            saltarEspacios();
            objeto.poner(clave, valor(profundidad + 1));
            saltarEspacios();
            if (actual() == ',') {
                pos++;
            } else {
                esperar('}');
                return objeto;
            }
        }
    }

    private JsonArreglo arreglo(int profundidad) {
        JsonArreglo arreglo = new JsonArreglo();
        esperar('[');
        saltarEspacios();
        if (actual() == ']') {
            pos++;
            return arreglo;
        }
        while (true) {
            saltarEspacios();
            arreglo.agregar(valor(profundidad + 1));
            saltarEspacios();
            if (actual() == ',') {
                pos++;
            } else {
                esperar(']');
                return arreglo;
            }
        }
    }

    private String cadena() {
        esperar('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = actual();
            pos++;
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = actual();
            pos++;
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > texto.length()) {
                        throw error("secuencia \\u incompleta");
                    }
                    try {
                        sb.append((char) Integer.parseInt(texto.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("secuencia \\u inválida");
                    }
                    pos += 4;
                }
                default -> throw error("secuencia de escape desconocida '\\" + e + "'");
            }
        }
    }

    private Object numero() {
        int inicio = pos;
        if (actual() == '-') {
            pos++;
        }
        boolean decimal = false;
        while (pos < texto.length()) {
            char c = texto.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                decimal = true;
                pos++;
            } else {
                break;
            }
        }
        String cifras = texto.substring(inicio, pos);
        try {
            return decimal ? (Object) Double.parseDouble(cifras) : (Object) Long.parseLong(cifras);
        } catch (NumberFormatException ex) {
            pos = inicio;
            throw error("número inválido '" + cifras + "'");
        }
    }

    // ---- escritura ----

    /** Escribe el valor como JSON con sangría de dos espacios. */
    public static String escribir(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(valor, sb, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void sangria(StringBuilder sb, int nivel) {
        for (int i = 0; i < nivel; i++) {
            sb.append("  ");
        }
    }

    private static void escribir(Object valor, StringBuilder sb, int nivel) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof JsonObjeto objeto) {
            escribirObjeto(objeto, sb, nivel);
        } else if (valor instanceof JsonArreglo arreglo) {
            escribirArreglo(arreglo, sb, nivel);
        } else if (valor instanceof String texto) {
            escribirCadena(texto, sb);
        } else if (valor instanceof Number || valor instanceof Boolean) {
            sb.append(valor);
        } else {
            throw new IllegalArgumentException("No se puede escribir un valor de tipo " + valor.getClass().getSimpleName() + " como JSON.");
        }
    }

    private static void escribirObjeto(JsonObjeto objeto, StringBuilder sb, int nivel) {
        if (objeto.tamanio() == 0) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        for (int i = 0; i < objeto.tamanio(); i++) {
            sangria(sb, nivel + 1);
            escribirCadena(objeto.claveEn(i), sb);
            sb.append(": ");
            escribir(objeto.valorEn(i), sb, nivel + 1);
            sb.append(i < objeto.tamanio() - 1 ? ",\n" : "\n");
        }
        sangria(sb, nivel);
        sb.append('}');
    }

    private static void escribirArreglo(JsonArreglo arreglo, StringBuilder sb, int nivel) {
        if (arreglo.tamanio() == 0) {
            sb.append("[]");
            return;
        }
        boolean sencillo = true;
        for (int i = 0; i < arreglo.tamanio(); i++) {
            Object v = arreglo.obtener(i);
            if (v instanceof JsonObjeto || v instanceof JsonArreglo) {
                sencillo = false;
            }
        }
        if (sencillo) {
            sb.append('[');
            for (int i = 0; i < arreglo.tamanio(); i++) {
                escribir(arreglo.obtener(i), sb, nivel);
                sb.append(i < arreglo.tamanio() - 1 ? ", " : "");
            }
            sb.append(']');
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < arreglo.tamanio(); i++) {
            sangria(sb, nivel + 1);
            escribir(arreglo.obtener(i), sb, nivel + 1);
            sb.append(i < arreglo.tamanio() - 1 ? ",\n" : "\n");
        }
        sangria(sb, nivel);
        sb.append(']');
    }

    private static void escribirCadena(String texto, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}

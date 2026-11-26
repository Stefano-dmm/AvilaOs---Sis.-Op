package avilaos.pruebas;

import static avilaos.pruebas.Verificador.igual;
import static avilaos.pruebas.Verificador.lanza;
import static avilaos.pruebas.Verificador.seccion;
import static avilaos.pruebas.Verificador.verificar;

import avilaos.json.Json;
import avilaos.json.JsonArreglo;
import avilaos.json.JsonObjeto;

/** El lector y escritor de JSON propios: el formato del enunciado, ida y vuelta, y los errores que debe señalar. */
public final class PruebasJson {

    private PruebasJson() {
    }

    private static final String EJEMPLO = """
            { "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
              "fileSystem": [
                { "path": "/mbr.bin", "type": "FILE", "startBlock": 0, "blocks": 11, "owner": "admin", "permissions": "public" },
                { "path": "/sistema", "type": "DIRECTORY", "owner": "admin", "permissions": "public" }
              ], "activo": true, "nada": null, "ratio": 1.5, "texto": "a\\"b\\\\c\\n\\u00e1" }
            """;

    public static void ejecutar() {
        lectura();
        escrituraIdaYVuelta();
        errores();
    }

    private static void lectura() {
        seccion("JSON: lectura del formato del enunciado");
        JsonObjeto raiz = Json.leerObjeto(EJEMPLO);
        JsonObjeto disco = raiz.objeto("diskConfig");
        igual(200, disco.entero("totalBlocks"), "lee un entero dentro de un objeto anidado");
        igual(50, disco.entero("initialHead"), "lee otro entero");
        JsonArreglo archivos = raiz.arreglo("fileSystem");
        igual(2, archivos.tamanio(), "lee un arreglo de objetos");
        igual("/mbr.bin", archivos.objeto(0).texto("path"), "lee un texto");
        igual(11, archivos.objeto(0).entero("blocks"), "lee los bloques del archivo");
        igual(7, archivos.objeto(1).entero("startBlock", 7), "un campo ausente toma su valor por defecto");
        verificar(Boolean.TRUE.equals(raiz.obtener("activo")), "lee un booleano");
        verificar(raiz.tiene("nada") && raiz.obtener("nada") == null, "lee null");
        igual(1.5, raiz.obtener("ratio"), "lee un decimal como Double");
        igual("a\"b\\c\ná", raiz.texto("texto"), "interpreta las secuencias de escape y \\u");
        igual(5, Json.leerObjeto("﻿{\"a\": 5}").entero("a"), "ignora la marca BOM del inicio del archivo");
        igual(0, Json.leerObjeto("{}").tamanio(), "lee un objeto vacío");
    }

    private static void escrituraIdaYVuelta() {
        seccion("JSON: escritura");
        JsonObjeto objeto = new JsonObjeto();
        objeto.poner("nombre", "ficha \"x\"");
        objeto.poner("bloques", 12L);
        JsonArreglo cadena = new JsonArreglo();
        cadena.agregar(3L);
        cadena.agregar(4L);
        objeto.poner("chain", cadena);
        JsonArreglo lista = new JsonArreglo();
        lista.agregar(new JsonObjeto().poner("a", 1L));
        objeto.poner("lista", lista);
        objeto.poner("vacio", new JsonArreglo());
        String texto = Json.escribir(objeto);
        JsonObjeto leido = Json.leerObjeto(texto);
        igual("ficha \"x\"", leido.texto("nombre"), "un texto con comillas sobrevive a escribir y volver a leer");
        igual(12, leido.entero("bloques"), "un entero sobrevive");
        igual(2, leido.arreglo("chain").tamanio(), "un arreglo de números sobrevive");
        igual(1, leido.arreglo("lista").objeto(0).entero("a"), "un arreglo de objetos sobrevive");
        igual(0, leido.arreglo("vacio").tamanio(), "un arreglo vacío sobrevive");
        objeto.poner("bloques", 99L);
        igual(99, Json.leerObjeto(Json.escribir(objeto)).entero("bloques"), "poner una clave repetida reemplaza su valor");
    }

    private static void errores() {
        seccion("JSON: entradas inválidas");
        lanza(IllegalArgumentException.class, () -> Json.leer("{\"a\": 1"), "falta la llave de cierre");
        lanza(IllegalArgumentException.class, () -> Json.leer("{\"a\": 1,}"), "coma sobrante antes de la llave");
        lanza(IllegalArgumentException.class, () -> Json.leer("{a: 1}"), "el nombre del campo debe ir entre comillas");
        lanza(IllegalArgumentException.class, () -> Json.leer("{\"a\": 1} basura"), "texto después del final");
        lanza(IllegalArgumentException.class, () -> Json.leer("{\"a\": \"sin cerrar}"), "texto sin cerrar");
        lanza(IllegalArgumentException.class, () -> Json.leer("{\"a\": 1.2.3}"), "número mal escrito");
        lanza(IllegalArgumentException.class, () -> Json.leer(""), "un archivo vacío");
        lanza(IllegalArgumentException.class, () -> Json.leer(null), "sin texto");
        lanza(IllegalArgumentException.class, () -> Json.leerObjeto("[1, 2]"), "la raíz debe ser un objeto");
        lanza(IllegalArgumentException.class, () -> Json.leer("[".repeat(200)), "demasiados niveles anidados no desbordan la pila");
        JsonObjeto o = Json.leerObjeto("{\"n\": 1.5, \"s\": \"x\", \"g\": 99999999999}");
        lanza(IllegalArgumentException.class, () -> o.entero("n"), "un decimal no es un entero");
        lanza(IllegalArgumentException.class, () -> o.entero("s"), "un texto no es un entero");
        lanza(IllegalArgumentException.class, () -> o.entero("g"), "un entero fuera del rango de int se rechaza");
        lanza(IllegalArgumentException.class, () -> o.entero("falta"), "falta un entero obligatorio");
        lanza(IllegalArgumentException.class, () -> o.texto("n"), "un número no es un texto");
        lanza(IllegalArgumentException.class, () -> o.objeto("s"), "un texto no es un objeto");
        lanza(IllegalArgumentException.class, () -> o.arreglo("falta"), "falta un arreglo obligatorio");
    }
}

package avilaos.pruebas;

/** Utilidad minima para las pruebas automaticas (sin librerias externas). */
public final class Verificador {

    private static int pasadas;
    private static int fallidas;

    private Verificador() {
    }

    public static void seccion(String titulo) {
        System.out.println();
        System.out.println("== " + titulo);
    }

    public static void verificar(boolean condicion, String nombre) {
        if (condicion) {
            pasadas++;
            System.out.println("  OK    " + nombre);
        } else {
            fallidas++;
            System.out.println("  FALLA " + nombre);
        }
    }

    public static void igual(Object esperado, Object real, String nombre) {
        boolean ok = (esperado == null) ? real == null : esperado.equals(real);
        if (ok) {
            verificar(true, nombre);
        } else {
            verificar(false, nombre + " (esperado " + esperado + ", real " + real + ")");
        }
    }

    /** Verifica que el bloque lance una excepcion de la clase indicada. */
    public static void lanza(Class<? extends RuntimeException> tipo, Runnable bloque, String nombre) {
        try {
            bloque.run();
            verificar(false, nombre + " (no lanzo excepcion)");
        } catch (RuntimeException ex) {
            verificar(tipo.isInstance(ex), nombre + (tipo.isInstance(ex) ? "" : " (lanzo " + ex + ")"));
        }
    }

    public static int resumen() {
        System.out.println();
        System.out.println("Pruebas pasadas: " + pasadas + " | fallidas: " + fallidas);
        return fallidas;
    }
}

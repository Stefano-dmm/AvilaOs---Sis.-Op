package avilaos.pruebas;

/** Punto de entrada de las pruebas automaticas: Run File en NetBeans o java avilaos.pruebas.EjecutarPruebas. */
public final class EjecutarPruebas {

    private EjecutarPruebas() {
    }

    public static void main(String[] args) throws Exception {
        PruebasSemaforos.ejecutar();
        PruebasBuffer.ejecutar();
        PruebasProgramas.ejecutar();
        PruebasIntegracion.ejecutar();
        PruebasReloj.ejecutar();
        PruebasBalanceo.ejecutar();
        PruebasConfiguracion.ejecutar();
        PruebasFormularios.ejecutar();
        int fallidas = Verificador.resumen();
        if (fallidas > 0) {
            System.exit(1);
        }
    }
}

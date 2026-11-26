package avilaos.disco;

/**
 * Lo que una política de disco decide: qué solicitud se atiende y por dónde viaja el cabezal para llegar a ella.
 * El viaje es una lista de tramos: el cabezal va hasta destinos[i] y, si saltos[i] no es negativo, aparece en
 * saltos[i] sin recorrer la distancia (el salto de C-SCAN, que no cuenta como desplazamiento).
 */
public record PlanMovimiento(SolicitudES solicitud, int[] destinos, int[] saltos, DireccionCabezal direccionFinal) {

    /** Plan de un solo tramo: va directo al bloque de la solicitud. */
    public static PlanMovimiento directo(SolicitudES solicitud, DireccionCabezal direccionFinal) {
        return new PlanMovimiento(solicitud, new int[] {solicitud.getBloque()}, new int[] {-1}, direccionFinal);
    }

    /** Plan de dos tramos: llega primero a un extremo (con un salto opcional) y desde ahí va a la solicitud. */
    public static PlanMovimiento porExtremo(SolicitudES solicitud, int extremo, int saltoDesdeExtremo,
                                            DireccionCabezal direccionFinal) {
        return new PlanMovimiento(solicitud, new int[] {extremo, solicitud.getBloque()},
                new int[] {saltoDesdeExtremo, -1}, direccionFinal);
    }
}

package avilaos.contratos;

/**
 * Contrato para componentes que avanzan con el reloj global.
 * En cada tic, el componente debe avanzar exactamente un ciclo.
 */
public interface Ciclico {

    void onTick(int cicloGlobal);
}

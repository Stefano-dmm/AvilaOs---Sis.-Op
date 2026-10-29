/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package avilaos.hardware;
/**
 * Todo componente que reacciona al reloj global (el computador, y mas adelante un DMA) comparte
 * esta interfaz: en cada ciclo el reloj llama a tic exactamente una vez.
 *
 * @author bethe
 */
public interface Clockable {
    void tic(long cicloActual);
}
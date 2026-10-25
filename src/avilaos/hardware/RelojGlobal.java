/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

/**
 *
 * @author bethe
 */
import java.util.ArrayList;
import proyecto.avilaos.jesús.bethencourt.Clockable;
public class RelojGlobal implements Runnable {
    private long cicloActual;
    private boolean activo;
    private long velocidadMs; // Tiempo en milisegundos por cada tic del reloj
    private ArrayList<Clockable> suscriptores;

    public RelojGlobal(long velocidadMs) {
        this.cicloActual = 0;
        this.activo = false;
        this.velocidadMs = velocidadMs;
        this.suscriptores = new ArrayList<>();
    }

    public void suscribir(Clockable c) {
        if (!suscriptores.contains(c)) {
            suscriptores.add(c);
        }
    }

    public void desuscribir(Clockable c) {
        suscriptores.remove(c);
    }

    @Override
    public void run() {
        activo = true;
        while (activo) {
            try {
                Thread.sleep(velocidadMs);
                cicloActual++;
                notificarSuscriptores();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void notificarSuscriptores() {
        // Recorremos los suscriptores para avisarles que ocurrió un nuevo ciclo
        for (int i = 0; i < suscriptores.size(); i++) {
            suscriptores.get(i).tick(cicloActual);
        }
    }

    public void detener() {
        this.activo = false;
    }

    // ================= Getters y Setters =================

    public long getCicloActual() {
        return cicloActual;
    }

    public void setVelocidadMs(long velocidadMs) {
        this.velocidadMs = velocidadMs;
    }

    public boolean isActivo() {
        return activo;
    }
}

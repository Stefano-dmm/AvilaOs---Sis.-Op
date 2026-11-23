/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.memoria;

import avilaos.estructuras.ListaEnlazada;
import avilaos.modelo.Proceso;

/**
 * Simula el almacenamiento secundario / área de intercambio (swap) donde se
 * respaldan procesos suspendidos cuando se requiere liberar memoria principal.
 *
 * @author bethe
 */
public class DiscoSecundario {

    private final int capacidadTotal;
    private int espacioUsado;
    private final ListaEnlazada<Proceso> procesosEnDisco;

    /**
     * Crea un disco secundario con una capacidad máxima determinada.
     *
     * @param capacidadTotal capacidad total disponible en disco (debe ser mayor que 0)
     * @throws IllegalArgumentException si {@code capacidadTotal <= 0}
     */
    public DiscoSecundario(int capacidadTotal) {
        if (capacidadTotal <= 0) {
            throw new IllegalArgumentException("La capacidad del disco debe ser mayor que cero.");
        }
        this.capacidadTotal = capacidadTotal;
        this.espacioUsado = 0;
        this.procesosEnDisco = new ListaEnlazada<>();
    }

    /**
     * Indica si hay suficiente espacio libre en disco para almacenar la memoria requerida.
     *
     * @param memoriaRequerida cantidad de memoria a respaldar
     * @return {@code true} si cabe en el espacio restante; {@code false} en caso contrario
     */
    public boolean tieneEspacio(int memoriaRequerida) {
        if (memoriaRequerida < 0) {
            return false;
        }
        return espacioUsado + memoriaRequerida <= capacidadTotal;
    }

    /**
     * Guarda un proceso en disco si no es nulo, no está ya respaldado y hay espacio libre.
     *
     * @param proceso proceso a respaldar en almacenamiento secundario
     * @return {@code true} si se guardó correctamente; {@code false} si no pudo guardarse
     */
    public boolean guardarProceso(Proceso proceso) {
        if (proceso == null || procesosEnDisco.contiene(proceso)) {
            return false;
        }
        int requerido = proceso.getMemoriaRequerida();
        if (!tieneEspacio(requerido)) {
            return false;
        }
        procesosEnDisco.agregarAlFinal(proceso);
        espacioUsado += requerido;
        return true;
    }

    /**
     * Verifica si un proceso se encuentra actualmente almacenado en el disco.
     *
     * @param proceso proceso a consultar
     * @return {@code true} si está en disco; {@code false} en caso contrario
     */
    public boolean contieneProceso(Proceso proceso) {
        return proceso != null && procesosEnDisco.contiene(proceso);
    }

    /**
     * Elimina un proceso del disco y libera el espacio ocupado por su memoria requerida.
     *
     * @param proceso proceso a retirar del disco
     * @return {@code true} si el proceso existía y fue liberado; {@code false} en caso contrario
     */
    public boolean liberarProceso(Proceso proceso) {
        if (proceso == null) {
            return false;
        }
        if (procesosEnDisco.eliminar(proceso)) {
            espacioUsado -= proceso.getMemoriaRequerida();
            if (espacioUsado < 0) {
                espacioUsado = 0;
            }
            return true;
        }
        return false;
    }

    /** @return capacidad total del disco secundario. */
    public int getCapacidadTotal() {
        return capacidadTotal;
    }

    /** @return cantidad de memoria actualmente ocupada en disco. */
    public int getEspacioUsado() {
        return espacioUsado;
    }

    /** @return cantidad de memoria libre disponible en disco. */
    public int getEspacioDisponible() {
        return capacidadTotal - espacioUsado;
    }

    /** @return lista enlazada con los procesos almacenados en disco. */
    public ListaEnlazada<Proceso> getProcesosEnDisco() {
        return procesosEnDisco;
    }
}
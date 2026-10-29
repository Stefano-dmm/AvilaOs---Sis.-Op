/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

import avilaos.modelo.ModoCPU;
import avilaos.modelo.Proceso;

/**
 * Procesador de un computador (base: clase de B). Solo guarda registros y modo; no decide nada:
 * quien decide que proceso corre es el sistema operativo del computador.
 *
 * @author bethe
 */
public class CPU {
    private int pc;           // Program Counter (Contador de programa)
    private int mar;          // Memory Address Register (Registro de direcciones de memoria)
    private ModoCPU modo;     // Usuario o sistema operativo
    private Proceso procesoActual;

    /** Crea una CPU libre, en modo sistema operativo y con los registros en 0. */
    public CPU() {
        this.pc = 0;
        this.mar = 0;
        this.modo = ModoCPU.SISTEMA_OPERATIVO;
        this.procesoActual = null;
    }

    /** Carga un proceso (restaurando sus registros) o deja la CPU libre en modo sistema si es null. */
    public void cargarProceso(Proceso p) {
        this.procesoActual = p;
        if (p != null) {
            this.pc = p.getPc();
            this.mar = p.getPc();
            this.modo = ModoCPU.USUARIO;
        } else {
            this.pc = 0;
            this.mar = 0;
            this.modo = ModoCPU.SISTEMA_OPERATIVO;
        }
    }

    /** Ejecucion lineal: PC y MAR avanzan juntos, una unidad por instruccion ejecutada. */
    public void registrarPc(int direccion) {
        this.pc = direccion;
        this.mar = direccion;
    }

    // Getters y Setters

    public int getPc() {
        return pc;
    }

    public void setPc(int pc) {
        this.pc = pc;
    }

    public int getMar() {
        return mar;
    }

    public void setMar(int mar) {
        this.mar = mar;
    }

    public ModoCPU getModo() {
        return modo;
    }

    public void setModo(ModoCPU modo) {
        this.modo = modo;
    }

    public Proceso getProcesoActual() {
        return procesoActual;
    }

    /** Verdadero si hay un proceso cargado. */
    public boolean estaOcupada() {
        return procesoActual != null;
    }
}

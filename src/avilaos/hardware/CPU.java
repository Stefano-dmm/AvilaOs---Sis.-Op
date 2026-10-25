/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

import avilaos.modelo.Proceso;

/**
 *
 * @author bethe
 */
public class CPU {
    private int pc;           // Program Counter (Contador de programa)
    private int mar;          // Memory Address Register (Registro de direcciones de memoria)
    private String modo;      // "USUARIO" o "SISTEMA"
    private Proceso procesoActual;

    public CPU() {
        this.pc = 0;
        this.mar = 0;
        this.modo = "USUARIO";
        this.procesoActual = null;
    }

    /**
     * Se Ejecuta un ciclo de instrucción del proceso actual.
     * Es llamado desde el computador o el reloj global.
     */
    public void ejecutarCiclo() {
        if (procesoActual != null) {
            // Avanza el PC del procesador
            pc++;
            // Llama al método del proceso para consumir una instrucción/ciclo de su ráfaga
            procesoActual.ejecutarCiclo(); 
            
            // Si el proceso ya terminó sus instrucciones, se maneja desde el núcleo/planificador
        }
    }

    public void cargarProceso(Proceso p) {
        this.procesoActual = p;
        if (p != null) {
            this.pc = p.getInstructionPointer(); // O el contador inicial del proceso
            this.modo = "USUARIO";
        } else {
            this.pc = 0;
            this.modo = "SISTEMA"; // Si no hay proceso, la CPU pasa a modo sistema/ociosa
        }
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

    public String getModo() {
        return modo;
    }

    public void setModo(String modo) {
        this.modo = modo;
    }

    public Proceso getProcesoActual() {
        return procesoActual;
    }

    public boolean estaOcupada() {
        return procesoActual != null;
    }
}
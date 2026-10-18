package avilaos.nucleo;

import avilaos.contratos.Ciclico;
import avilaos.contratos.PoliticaPlanificacionStrategy;
import avilaos.estructuras.Cola;
import avilaos.modelo.EstadoProceso;
import avilaos.modelo.ModoCPU;
import avilaos.modelo.PoliticaPlanificacion;
import avilaos.modelo.Proceso;

public class Computador implements Ciclico {

    private static int contadorIds = 0;

    private final int id;
    private String nombre;
    private int ramTotal;
    private int ramUsada;
    private ModoCPU modoCPU;
    private int quantum;
    private int quantumRestante;
    private Proceso procesoEnCPU;
    private PoliticaPlanificacionStrategy politica;
    private PoliticaPlanificacion nombrePolitica;

    private final Cola<Proceso> colaNuevos = new Cola<>();
    private final Cola<Proceso> colaListos = new Cola<>();
    private final Cola<Proceso> colaBloqueados = new Cola<>();
    private final Cola<Proceso> colaTerminados = new Cola<>();

    public Computador(String nombre, int ramTotal, int quantum) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del computador no puede estar vacío.");
        }
        if (ramTotal <= 0) {
            throw new IllegalArgumentException("La RAM debe ser mayor que 0.");
        }
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor que 0.");
        }

        this.id = ++contadorIds;
        this.nombre = nombre.trim();
        this.ramTotal = ramTotal;
        this.ramUsada = 0;
        this.modoCPU = ModoCPU.SISTEMA_OPERATIVO;
        this.quantum = quantum;
        this.quantumRestante = quantum;
        this.procesoEnCPU = null;
        this.politica = null;
        this.nombrePolitica = null;
    }

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getRamTotal() {
        return ramTotal;
    }

    public void setRamTotal(int ramTotal) {
        this.ramTotal = ramTotal;
    }

    public int getRamUsada() {
        return ramUsada;
    }

    public int getRamLibre() {
        return ramTotal - ramUsada;
    }

    public ModoCPU getModoCPU() {
        return modoCPU;
    }

    public void setModoCPU(ModoCPU modoCPU) {
        this.modoCPU = modoCPU;
    }

    public int getQuantum() {
        return quantum;
    }

    public void setQuantum(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor que 0.");
        }
        this.quantum = quantum;
        this.quantumRestante = quantum;
    }

    public int getQuantumRestante() {
        return quantumRestante;
    }

    public void setQuantumRestante(int quantumRestante) {
        this.quantumRestante = quantumRestante;
    }

    public Proceso getProcesoEnCPU() {
        return procesoEnCPU;
    }

    public void setProcesoEnCPU(Proceso procesoEnCPU) {
        this.procesoEnCPU = procesoEnCPU;
    }

    public PoliticaPlanificacionStrategy getPolitica() {
        return politica;
    }

    public PoliticaPlanificacion getNombrePolitica() {
        return nombrePolitica;
    }

    public void setPolitica(PoliticaPlanificacionStrategy politica, PoliticaPlanificacion nombre) {
        this.politica = politica;
        this.nombrePolitica = nombre;
    }

    public Cola<Proceso> getColaNuevos() {
        return colaNuevos;
    }

    public Cola<Proceso> getColaListos() {
        return colaListos;
    }

    public Cola<Proceso> getColaBloqueados() {
        return colaBloqueados;
    }

    public Cola<Proceso> getColaTerminados() {
        return colaTerminados;
    }

    public void reservarMemoria(int cantidad) {
        if (cantidad < 0 || cantidad > getRamLibre()) {
            throw new IllegalStateException("No hay memoria suficiente en " + nombre);
        }
        ramUsada += cantidad;
    }

    public void liberarMemoria(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("Cantidad de memoria inválida.");
        }
        ramUsada -= cantidad;
        if (ramUsada < 0) {
            ramUsada = 0;
        }
    }

    public void recibirProceso(Proceso proceso) {
        if (proceso == null) {
            throw new IllegalArgumentException("El proceso no puede ser null.");
        }
        proceso.setComputadorId(id);
        proceso.setEstado(EstadoProceso.NUEVO);
        colaNuevos.encolar(proceso);
        intentarAdmitirNuevos();
    }

    public int intentarAdmitirNuevos() {
        int admitidos = 0;
        int pendientes = colaNuevos.getTamanio();

        for (int i = 0; i < pendientes; i++) {
            Proceso proceso = colaNuevos.desencolar();
            if (proceso == null) {
                break;
            }

            if (proceso.getMemoriaRequerida() <= getRamLibre()) {
                reservarMemoria(proceso.getMemoriaRequerida());
                proceso.setEstado(EstadoProceso.LISTO);
                pasarAListos(proceso);
                admitidos++;
            } else {
                colaNuevos.encolar(proceso);
            }
        }

        return admitidos;
    }

    public void terminarProceso(Proceso proceso) {
        if (proceso == null) {
            return;
        }

        if (procesoEnCPU == proceso) {
            procesoEnCPU = null;
            modoCPU = ModoCPU.SISTEMA_OPERATIVO;
        }

        colaListos.getLista().eliminar(proceso);
        colaBloqueados.getLista().eliminar(proceso);
        colaNuevos.getLista().eliminar(proceso);

        if (proceso.getEstado() != EstadoProceso.NUEVO) {
            liberarMemoria(proceso.getMemoriaRequerida());
        }

        proceso.setEstado(EstadoProceso.TERMINADO);
        if (!colaTerminados.getLista().contiene(proceso)) {
            colaTerminados.encolar(proceso);
        }

        intentarAdmitirNuevos();
    }

    private void pasarAListos(Proceso proceso) {
        if (politica != null) {
            politica.alLlegar(proceso, colaListos);
        } else {
            colaListos.encolar(proceso);
        }
    }

    public static void reiniciarContadorIds() {
        contadorIds = 0;
    }

    @Override
    public void onTick(int cicloGlobal) {
    }

    @Override
    public String toString() {
        String cpu = (procesoEnCPU == null) ? "idle" : procesoEnCPU.getEtiqueta();
        String pol = (nombrePolitica == null) ? "sin-politica" : nombrePolitica.toString();
        return "PC" + id + "-" + nombre
                + " [RAM " + ramUsada + "/" + ramTotal
                + ", CPU=" + cpu
                + ", modo=" + modoCPU
                + ", pol=" + pol
                + ", q=" + quantum
                + ", nuevos=" + colaNuevos.getTamanio()
                + ", listos=" + colaListos.getTamanio()
                + ", bloq=" + colaBloqueados.getTamanio()
                + ", term=" + colaTerminados.getTamanio() + "]";
    }
}

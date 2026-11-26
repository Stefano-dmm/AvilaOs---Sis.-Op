package avilaos.disco;

import avilaos.contratos.PoliticaDiscoStrategy;
import avilaos.disco.politicas.FabricaPoliticasDisco;
import avilaos.estructuras.Cola;
import avilaos.estructuras.Nodo;
import avilaos.hardware.Clockable;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * Disco local simulado: un componente de hardware que obedece al reloj como la CPU. Tiene un mapa de bloques con
 * asignación encadenada, un área de intercambio (swap) al final, un cabezal y una única cola de E/S. En cada ciclo
 * atiende una sola solicitud: primero mueve el cabezal hasta su bloque (velocidad posiciones por ciclo) y luego
 * transfiere sus bloques (uno por ciclo). La política decide el orden y se puede cambiar en ejecución.
 *
 * <p><b>Numeración.</b> Los bloques [0, bloquesArchivos) son el área de archivos y los siguientes, hasta
 * bloquesArchivos + bloquesSwap, son el área de intercambio. SCAN y C-SCAN llegan hasta el último bloque del área de
 * archivos, o hasta el de la solicitud más alta si hay una en el swap.
 *
 * <p><b>Concurrencia.</b> El disco corre en su propio hilo (lo despierta el reloj) mientras otros hilos agregan
 * solicitudes y reservan o liberan bloques. El candado protege la cola, el mapa, el cabezal y las métricas. El
 * oyente se avisa sin el candado tomado, porque al reaccionar vuelve a llamar al disco.
 */
public class Disco implements Clockable {

    /** Bloques del área de archivos. */
    private final int bloquesArchivos;
    /** Bloques del área de intercambio, a continuación del área de archivos. */
    private final int bloquesSwap;
    /** Mapa de bloques con las cadenas de cada archivo y de cada proceso suspendido. Protegido por candado. */
    private final MapaBloques mapa;
    /** COLA DE E/S: la única cola de solicitudes del disco, en orden de llegada. Protegida por candado. */
    private final Cola<SolicitudES> cola = new Cola<>();
    /**
     * Semaphore de Java que protege la cola, el mapa, el cabezal y las métricas del disco, porque los hilos de los
     * computadores y de la interfaz las usan mientras el hilo del disco atiende solicitudes.
     */
    private final Semaphore candado = new Semaphore(1);

    /** Bloque donde está el cabezal. */
    private int posicion;
    /** Dirección en que se mueve el cabezal. */
    private DireccionCabezal direccion = DireccionCabezal.SUBE;
    /** Posiciones que recorre el cabezal por ciclo de reloj. */
    private int velocidad;
    /** Nombre de la política en uso (lo que muestra el selector). */
    private PoliticaDisco nombrePolitica;
    /** Estrategia que decide qué solicitud se atiende (interfaz: se cambia en ejecución). */
    private PoliticaDiscoStrategy politica;

    /** Solicitud que se está atendiendo; null si el disco está libre. */
    private SolicitudES enServicio;
    /** Viaje del cabezal de la solicitud en servicio. */
    private PlanMovimiento plan;
    /** Tramo del viaje en que va el cabezal. */
    private int tramo;
    /** Bloques que faltan por transferir de la solicitud en servicio. */
    private int transferenciasRestantes;
    /** Política con la que se eligió la solicitud en servicio (para contar las atendidas por política). */
    private PoliticaDisco politicaDelServicio;

    /** Si es verdadero el disco termina lo que está atendiendo pero no empieza solicitudes nuevas, para dejar que la cola se llene y comparar políticas. */
    private volatile boolean retenido;
    /** Quien se entera de las solicitudes terminadas. */
    private OyenteDisco oyente;
    /** Destino de los mensajes del log (el cluster); puede ser null. */
    private Consumer<String> registro;

    /** Posiciones que el cabezal ha recorrido desde el comienzo. */
    private int desplazamientoTotal;
    /** Solicitudes atendidas en total. */
    private int atendidas;
    /** Solicitudes atendidas con cada política (indexado por el ordinal del enum). */
    private final int[] atendidasPorPolitica = new int[PoliticaDisco.values().length];
    /** Suma de los ciclos que las solicitudes esperaron en la cola antes de que el disco empezara a atenderlas. */
    private long esperaTotal;
    /** Serie del desplazamiento acumulado para el gráfico. */
    private final SerieDesplazamiento serie = new SerieDesplazamiento();

    /** Crea el disco con su tamaño, su swap, el cabezal en la posición inicial y la política inicial; valida todos los rangos. */
    public Disco(int bloquesArchivos, int bloquesSwap, int posicionInicial, int velocidad, PoliticaDisco politicaInicial) {
        if (bloquesArchivos < 1) {
            throw new IllegalArgumentException("El disco necesita al menos un bloque.");
        }
        if (bloquesSwap < 0) {
            throw new IllegalArgumentException("El área de intercambio no puede ser negativa.");
        }
        if (posicionInicial < 0 || posicionInicial >= bloquesArchivos) {
            throw new IllegalArgumentException("La posición inicial del cabezal debe estar entre 0 y " + (bloquesArchivos - 1) + ".");
        }
        if (velocidad < 1) {
            throw new IllegalArgumentException("El cabezal debe recorrer al menos una posición por ciclo.");
        }
        this.bloquesArchivos = bloquesArchivos;
        this.bloquesSwap = bloquesSwap;
        this.mapa = new MapaBloques(bloquesArchivos + bloquesSwap);
        this.posicion = posicionInicial;
        this.velocidad = velocidad;
        setPolitica(politicaInicial);
    }

    // ---- solicitudes y política ----

    /** Agrega una solicitud al final de la cola de E/S. */
    public void encolar(SolicitudES solicitud) {
        if (solicitud == null) {
            throw new IllegalArgumentException("La solicitud no puede ser null.");
        }
        if (solicitud.getBloque() >= mapa.getTotal()) {
            throw new IllegalArgumentException("El bloque " + solicitud.getBloque() + " no existe en el disco.");
        }
        candado.acquireUninterruptibly();
        try {
            cola.encolar(solicitud);
        } finally {
            candado.release();
        }
    }

    /**
     * Saca de la cola las solicitudes que esperan de un proceso (porque terminó por deadline) y las devuelve. La que ya
     * se está atendiendo no se cancela: el disco la termina y quien la recibe decide qué hacer.
     */
    public SolicitudES[] cancelarDe(int procesoId) {
        candado.acquireUninterruptibly();
        try {
            int cuenta = 0;
            Nodo<SolicitudES> actual = cola.getLista().getCabeza();
            while (actual != null) {
                if (actual.getDato().getProcesoId() == procesoId) {
                    cuenta++;
                }
                actual = actual.getSiguiente();
            }
            SolicitudES[] cancelladas = new SolicitudES[cuenta];
            int i = 0;
            actual = cola.getLista().getCabeza();
            while (actual != null) {
                if (actual.getDato().getProcesoId() == procesoId) {
                    cancelladas[i++] = actual.getDato();
                }
                actual = actual.getSiguiente();
            }
            for (SolicitudES s : cancelladas) {
                cola.getLista().eliminar(s);
            }
            return cancelladas;
        } finally {
            candado.release();
        }
    }

    /** Cambia la política de planificación en ejecución; la solicitud que ya se está atendiendo no cambia. */
    public void setPolitica(PoliticaDisco nombre) {
        if (nombre == null) {
            throw new IllegalArgumentException("Falta la política del disco.");
        }
        candado.acquireUninterruptibly();
        try {
            this.nombrePolitica = nombre;
            this.politica = FabricaPoliticasDisco.crear(nombre);
        } finally {
            candado.release();
        }
    }

    /** Retener: el disco no empieza solicitudes nuevas y la cola se acumula; soltar lo deja atender con la política activa. */
    public void setRetenido(boolean retenido) {
        this.retenido = retenido;
    }

    public boolean isRetenido() {
        return retenido;
    }

    public PoliticaDisco getNombrePolitica() {
        candado.acquireUninterruptibly();
        try {
            return nombrePolitica;
        } finally {
            candado.release();
        }
    }

    /** Quien se entera de las solicitudes que terminan. */
    public void setOyente(OyenteDisco oyente) {
        this.oyente = oyente;
    }

    /** Destino de los mensajes del log. */
    public void setRegistro(Consumer<String> registro) {
        this.registro = registro;
    }

    // ---- reloj ----

    /** Lo llama el reloj global: el disco avanza exactamente un ciclo de trabajo. */
    @Override
    public void tic(long ciclo) {
        SolicitudES terminada = null;
        candado.acquireUninterruptibly();
        try {
            if (enServicio == null && !cola.estaVacia() && !retenido) {
                iniciarServicio((int) ciclo);
            }
            if (enServicio != null) {
                terminada = avanzarUnCiclo();
            }
            serie.agregar((int) ciclo, desplazamientoTotal, nombrePolitica.ordinal());
        } finally {
            candado.release();
        }
        if (terminada != null) {
            avisar("El disco termina la solicitud de " + terminada.getEtiquetaProceso() + " (" + terminada.getOperacion() + ")");
            if (oyente != null) {
                oyente.solicitudAtendida(terminada, (int) ciclo);
            }
        }
    }

    /** Saca de la cola la solicitud que elige la política y prepara el viaje del cabezal. Se llama con el candado tomado. */
    private void iniciarServicio(int ciclo) {
        PlanMovimiento elegido = politica.seleccionar(cola, posicion, direccion, ultimoBloqueDelBarrido());
        cola.getLista().eliminar(elegido.solicitud());
        enServicio = elegido.solicitud();
        plan = elegido;
        tramo = 0;
        transferenciasRestantes = enServicio.getBloquesATransferir();
        politicaDelServicio = nombrePolitica;
        enServicio.setCicloInicio(ciclo);
        esperaTotal += ciclo - enServicio.getCicloLlegada();
        avisar("El disco atiende la solicitud de " + enServicio.getEtiquetaProceso() + " en el bloque "
                + enServicio.getBloque() + " (" + nombrePolitica + ")");
    }

    /** Extremo alto del barrido: el último bloque de archivos, o más arriba si hay una solicitud en el swap. */
    private int ultimoBloqueDelBarrido() {
        int ultimo = bloquesArchivos - 1;
        Nodo<SolicitudES> actual = cola.getLista().getCabeza();
        while (actual != null) {
            ultimo = Math.max(ultimo, actual.getDato().getBloque());
            actual = actual.getSiguiente();
        }
        return ultimo;
    }

    /**
     * Un ciclo de trabajo de la solicitud en servicio: si el cabezal aún no llegó se mueve velocidad posiciones; si ya
     * llegó transfiere un bloque. Devuelve la solicitud si este ciclo la terminó. Se llama con el candado tomado.
     */
    private SolicitudES avanzarUnCiclo() {
        boolean cicloGastado = false;
        while (!cicloGastado && tramo < plan.destinos().length) {
            int destino = plan.destinos()[tramo];
            if (posicion == destino) {
                cerrarTramo();
                continue;
            }
            int paso = Math.min(velocidad, Math.abs(destino - posicion));
            direccion = destino > posicion ? DireccionCabezal.SUBE : DireccionCabezal.BAJA;
            posicion += direccion == DireccionCabezal.SUBE ? paso : -paso;
            desplazamientoTotal += paso;
            cicloGastado = true;
            if (posicion == destino) {
                cerrarTramo();
            }
        }
        if (cicloGastado) {
            return null;
        }
        transferenciasRestantes--;
        if (transferenciasRestantes > 0) {
            return null;
        }
        SolicitudES terminada = enServicio;
        direccion = plan.direccionFinal();
        atendidas++;
        atendidasPorPolitica[politicaDelServicio.ordinal()]++;
        enServicio = null;
        plan = null;
        return terminada;
    }

    /** El cabezal llegó al destino del tramo: aplica el salto de C-SCAN si lo hay y pasa al siguiente tramo. */
    private void cerrarTramo() {
        int salto = plan.saltos()[tramo];
        if (salto >= 0) {
            posicion = salto;
        }
        tramo++;
    }

    private void avisar(String mensaje) {
        Consumer<String> destino = registro;
        if (destino != null) {
            destino.accept(mensaje);
        }
    }

    // ---- bloques: archivos y swap ----

    /** Asigna bloques del área de archivos (encadenados); devuelve el primero o -1 si no hay suficientes libres. */
    public int reservarBloquesArchivo(int cantidad, int duenoId) {
        candado.acquireUninterruptibly();
        try {
            return mapa.asignar(cantidad, duenoId, 0, bloquesArchivos);
        } finally {
            candado.release();
        }
    }

    /** Asigna bloques contiguos del área de archivos desde inicio (carga del JSON); false si no se puede. */
    public boolean reservarBloquesDesde(int inicio, int cantidad, int duenoId) {
        if (inicio + cantidad > bloquesArchivos) {
            return false;
        }
        candado.acquireUninterruptibly();
        try {
            return mapa.asignarContiguos(inicio, cantidad, duenoId);
        } finally {
            candado.release();
        }
    }

    /** Asigna exactamente los bloques indicados del área de archivos, encadenados en ese orden; false si no se puede. */
    public boolean reservarCadena(int[] bloques, int duenoId) {
        for (int b : bloques) {
            if (b >= bloquesArchivos) {
                return false;
            }
        }
        candado.acquireUninterruptibly();
        try {
            return mapa.asignarCadena(bloques, duenoId);
        } finally {
            candado.release();
        }
    }

    /** Asigna bloques del área de intercambio para un proceso suspendido; devuelve el primero o -1 si no caben. */
    public int reservarBloquesSwap(int cantidad, int procesoId) {
        if (bloquesSwap == 0) {
            return -1;
        }
        candado.acquireUninterruptibly();
        try {
            return mapa.asignar(cantidad, -procesoId, bloquesArchivos, bloquesArchivos + bloquesSwap);
        } finally {
            candado.release();
        }
    }

    /** Libera la cadena de bloques que empieza en primero (de un archivo o de un proceso suspendido). */
    public int liberarBloques(int primero) {
        candado.acquireUninterruptibly();
        try {
            return mapa.liberar(primero);
        } finally {
            candado.release();
        }
    }

    // ---- lectura del estado (la interfaz y las métricas) ----

    public int getBloquesArchivos() {
        return bloquesArchivos;
    }

    public int getBloquesSwap() {
        return bloquesSwap;
    }

    public int getTotalBloques() {
        return bloquesArchivos + bloquesSwap;
    }

    public int getPosicion() {
        candado.acquireUninterruptibly();
        try {
            return posicion;
        } finally {
            candado.release();
        }
    }

    public DireccionCabezal getDireccion() {
        candado.acquireUninterruptibly();
        try {
            return direccion;
        } finally {
            candado.release();
        }
    }

    public int getVelocidad() {
        return velocidad;
    }

    /** Cambia cuántas posiciones recorre el cabezal por ciclo (al menos 1). */
    public void setVelocidad(int velocidad) {
        if (velocidad < 1) {
            throw new IllegalArgumentException("El cabezal debe recorrer al menos una posición por ciclo.");
        }
        candado.acquireUninterruptibly();
        try {
            this.velocidad = velocidad;
        } finally {
            candado.release();
        }
    }

    /** Dueño del bloque: 0 libre, positivo un archivo, negativo un proceso suspendido en el swap. */
    public int getDueno(int bloque) {
        candado.acquireUninterruptibly();
        try {
            return mapa.getDueno(bloque);
        } finally {
            candado.release();
        }
    }

    /** Siguiente bloque de la cadena del bloque dado, o -1 si es el último. */
    public int getSiguiente(int bloque) {
        candado.acquireUninterruptibly();
        try {
            return mapa.getSiguiente(bloque);
        } finally {
            candado.release();
        }
    }

    public int getBloquesLibresArchivos() {
        candado.acquireUninterruptibly();
        try {
            return mapa.libresEn(0, bloquesArchivos);
        } finally {
            candado.release();
        }
    }

    public int getBloquesLibresSwap() {
        candado.acquireUninterruptibly();
        try {
            return mapa.libresEn(bloquesArchivos, bloquesArchivos + bloquesSwap);
        } finally {
            candado.release();
        }
    }

    /** Foto de la cola de E/S, en orden de llegada (sin la solicitud que se está atendiendo). */
    public SolicitudES[] copiarCola() {
        candado.acquireUninterruptibly();
        try {
            SolicitudES[] copia = new SolicitudES[cola.getTamanio()];
            int i = 0;
            Nodo<SolicitudES> actual = cola.getLista().getCabeza();
            while (actual != null) {
                copia[i++] = actual.getDato();
                actual = actual.getSiguiente();
            }
            return copia;
        } finally {
            candado.release();
        }
    }

    /** Solicitud que se atiende ahora; null si el disco está libre. */
    public SolicitudES getEnServicio() {
        candado.acquireUninterruptibly();
        try {
            return enServicio;
        } finally {
            candado.release();
        }
    }

    public int getTamanioCola() {
        candado.acquireUninterruptibly();
        try {
            return cola.getTamanio();
        } finally {
            candado.release();
        }
    }

    // ---- métricas ----

    /** Posiciones que el cabezal ha recorrido en total (los saltos de C-SCAN no cuentan). */
    public int getDesplazamientoTotal() {
        candado.acquireUninterruptibly();
        try {
            return desplazamientoTotal;
        } finally {
            candado.release();
        }
    }

    public int getAtendidas() {
        candado.acquireUninterruptibly();
        try {
            return atendidas;
        } finally {
            candado.release();
        }
    }

    /** Solicitudes atendidas mientras estaba en uso la política dada. */
    public int getAtendidas(PoliticaDisco politica) {
        candado.acquireUninterruptibly();
        try {
            return atendidasPorPolitica[politica.ordinal()];
        } finally {
            candado.release();
        }
    }

    /** Tiempo promedio, en ciclos, que una solicitud espera en la cola antes de que el disco empiece a atenderla; 0 si no hubo ninguna. */
    public double getEsperaPromedio() {
        candado.acquireUninterruptibly();
        try {
            int iniciadas = atendidas + (enServicio == null ? 0 : 1);
            return iniciadas == 0 ? 0 : (double) esperaTotal / iniciadas;
        } finally {
            candado.release();
        }
    }

    /** Copia de la serie del desplazamiento acumulado para el gráfico. */
    public SerieDesplazamiento copiarSerie() {
        candado.acquireUninterruptibly();
        try {
            return serie.copiar();
        } finally {
            candado.release();
        }
    }
}

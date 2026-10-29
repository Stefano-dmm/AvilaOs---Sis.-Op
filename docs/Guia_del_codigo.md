# Guía del código de ÁvilaOS

Esta guía explica **cómo está organizado el código y, sobre todo, quién protege qué**: qué hace cada semáforo, qué guarda cada cola, qué apunta cada puntero y qué hilo toca cada cosa. Todo el código fuente tiene además comentarios Javadoc en cada clase, campo y método importante, y una descripción por paquete (`package-info.java`).

## 1. Paquetes

| Paquete | Qué contiene |
|---|---|
| `avilaos.gui` | Ventanas (Swing). Solo observan y controlan; no tienen lógica del sistema operativo |
| `avilaos.config` | Configuración: validación, archivo CSV, formularios y construcción de un cluster |
| `avilaos.metricas` | Cálculo de métricas e historial de utilización del gráfico |
| `avilaos.nucleo` | `Computador` (núcleo de una máquina) y `Cluster` (el conjunto) |
| `avilaos.nucleo.politicas` | FCFS, EDF, Round Robin y prioridades apropiativas |
| `avilaos.nucleo.programas` | El "código" de cada tipo de proceso (CPU, E/S, productor-consumidor) |
| `avilaos.nucleo.balanceo` | Criterio de asignación automática de procesos |
| `avilaos.sincronizacion` | `SemaforoSO` y `BufferSO` |
| `avilaos.hardware` | `RelojGlobal`, `Clockable`, `CPU`, `RAM` |
| `avilaos.modelo` | `Proceso` (PCB) y los enums |
| `avilaos.contratos` | Interfaces: política, programa de proceso y criterio de asignación |
| `avilaos.estructuras` | `Nodo`, `ListaEnlazada`, `Cola`, `Pila` propias |
| `avilaos.pruebas` | Pruebas automáticas y experimentos |

## 2. Los hilos del programa

| Hilo | Quién lo crea | Qué hace |
|---|---|---|
| **Hilo de Swing** (EDT) | Java | Dibuja la interfaz y atiende los botones |
| **Hilo del reloj** (`reloj-global`) | `RelojGlobal` | Marca cada ciclo: despierta a los computadores y espera a que todos terminen |
| **Un hilo por computador** (`suscriptor-reloj`) | `RelojGlobal.suscribir` | Ejecuta el `tic` de su `Computador` cuando el reloj lo despierta |

Los hilos de los computadores corren **en paralelo** entre sí dentro de cada ciclo, pero **nunca a la vez que la interfaz**: el reloj mantiene tomado el cerrojo del cluster durante todo el ciclo.

## 3. Mapa de concurrencia: cada `Semaphore` de Java y qué protege

Todos son `java.util.concurrent.Semaphore`. Los de exclusión mutua tienen un permiso y se toman con `acquire` y se sueltan con `release`; los de señalización arrancan en 0.

| Semáforo (dónde está) | Tipo | Qué protege o señala | Quién lo toma |
|---|---|---|---|
| `Cluster.cerrojo` | Exclusión mutua | **Todo el estado de la simulación**: colas, CPU, RAM y procesos de todos los computadores, las listas de computadores y buffers, la configuración | El reloj, durante cada ciclo completo; la interfaz, antes de leer o modificar algo |
| `RelojGlobal.candadoSuscriptores` | Exclusión mutua | La lista de suscriptores (`suscriptores`): `suscribir` y `desuscribir` pueden ocurrir mientras el reloj la recorre | El hilo que suscribe o desuscribe, y el reloj al hacer la copia para notificar |
| `RelojGlobal.pasos` | Señalización | No protege datos: cada permiso es "un ciclo pendiente" cuando el reloj está en pausa. `paso()` e `iniciar()` lo liberan; el hilo del reloj lo toma para avanzar | El hilo del reloj |
| `Suscriptor.inicio` | Señalización | "Empieza el ciclo": el reloj lo libera para que el hilo de ese computador ejecute `tic` | El hilo del computador espera; el reloj libera |
| `Suscriptor.fin` | Señalización | "Terminé mi ciclo": es la **barrera** de fin de ciclo; el reloj espera uno por computador antes de pasar al siguiente | El hilo del computador libera; el reloj espera |
| `Computador.candadoDespertares` | Exclusión mutua | La **bandeja de despertares** (`despertares`): la escriben otros computadores y la lee éste | Otros computadores al despertar un proceso; este computador al vaciarla |
| `SemaforoSO.candado` | Exclusión mutua | El valor (`valor`) y la **cola de espera** (`espera`) de un semáforo del SO | Cualquier computador que haga `semWait`, `semSignal` o `cancelarEspera` |
| `BufferSO.candado` | Exclusión mutua | Los contadores `elementos`, `producidos` y `consumidos` de un buffer, para que otros hilos los lean sin ver valores a medias | Quien inserte, extraiga o lea los contadores |

**Dos niveles de semáforos, para no confundirlos.** Los `Semaphore` de Java de la tabla protegen las *estructuras de datos del simulador* entre hilos. Los `SemaforoSO` (`vacios`, `llenos`, `mutex`) son los semáforos *simulados* del sistema operativo: son los que bloquean a los *procesos* (no a los hilos de Java) y los que implementan el productor-consumidor. Cada `SemaforoSO` usa por dentro un `Semaphore` de Java (`candado`) para que dos computadores no corrompan su valor ni su cola.

## 4. Qué estructura es de quién (las colas y sus dueños)

`ListaEnlazada`, `Cola` y `Pila` **no son seguras entre hilos**. Cada instancia tiene un único dueño que la protege:

| Estructura | Qué guarda | Quién la protege |
|---|---|---|
| `Computador.colaNuevos` | Procesos esperando RAM (largo plazo) | El hilo de ese computador durante su `tic`; la interfaz solo con el cerrojo del cluster |
| `Computador.colaListos` | Procesos con RAM esperando la CPU (el orden lo decide la política) | Ídem |
| `Computador.colaBloqueados` | Procesos esperando un semáforo, una E/S o la red | Ídem |
| `Computador.colaTerminados` | Procesos terminados | Ídem |
| `Computador.despertares` | Avisos de procesos que otro computador despertó | `candadoDespertares` |
| `SemaforoSO.espera` | **Cola del semáforo**: procesos bloqueados en él, en orden FIFO | `SemaforoSO.candado` |
| `RAM.procesosEnMemoria` | Procesos con RAM asignada | El hilo del computador dueño de esa RAM |
| `Cluster.computadores`, `Cluster.buffers` | Los computadores y buffers | El cerrojo del cluster (solo cambian desde la interfaz o al construir) |
| `RelojGlobal.suscriptores` | Computadores suscritos al reloj | `RelojGlobal.candadoSuscriptores` |
| `Configuracion.*` | Computadores, buffers y procesos iniciales | Solo la usa el hilo de Swing |
| `HistorialUtilizacion.ocupadoPorCiclo` | Muestras de utilización por ciclo | Sus métodos son `synchronized`: lo escribe el hilo del reloj y lo lee el gráfico al pintar, en Swing y fuera del cerrojo del cluster |

**La `Pila`** es una de las estructuras propias que pide el enunciado, pero el núcleo no la necesita (las colas bastan para los estados); se conserva para extensiones como el Proyecto 2.

### Punteros de las estructuras

- **`Nodo`**: guarda un `dato` y un puntero `siguiente` al nodo que le sigue; el último apunta a `null`.
- **`ListaEnlazada`**: tiene `cabeza` (primer nodo, desde donde se recorre y se extrae), `cola` (último nodo, para agregar al final sin recorrer) y `tamanio`. Agregar al final y extraer del inicio son O(1); buscar y quitar un elemento intermedio son O(n).
- **`Cola`** usa la cabeza de la lista como frente (se desencola ahí) y su final para encolar. **`Pila`** usa la cabeza como cima.
- **Un proceso bloqueado en un semáforo** apunta de vuelta a él con `Proceso.semaforoBloqueo` (es `volatile`: lo escribe el semáforo desde el hilo de otro computador y lo lee el computador dueño del proceso).

## 5. Un ciclo, paso a paso

1. El hilo del reloj toma `Cluster.cerrojo`: la interfaz queda esperando.
2. Sube el contador de ciclo y libera `Suscriptor.inicio` de cada computador: todos corren **a la vez**.
3. Cada computador ejecuta `Computador.tic`:
   1. procesa su bandeja de despertares (bloqueados → listos);
   2. avanza los bloqueos por tiempo (E/S y red) y cuenta el tiempo bloqueado en semáforos;
   3. si la CPU está libre despacha según la política (o desaloja si hay una prioridad mayor);
   4. ejecuta **una instrucción** del proceso con su `ProgramaProceso`;
   5. baja el deadline de todos los procesos vivos y termina a los que llegan a 0.
4. Cada computador libera `Suscriptor.fin` al terminar. El reloj espera todos los `fin` (barrera): así cada computador avanza **exactamente un ciclo**.
5. El reloj avisa a la interfaz (registrar eventos y muestra de utilización, todavía con el cerrojo tomado) y suelta el cerrojo.
6. La interfaz, en el hilo de Swing, toma el cerrojo, lee el estado y se redibuja.

## 6. Cómo se bloquea y se despierta un proceso entre computadores

1. El proceso P (computador 1) hace `semWait` sobre un semáforo sin unidades. `SemaforoSO.semWait` (con su candado) lo deja en la cola `espera` y devuelve `false`.
2. `ProgramaProductorConsumidor` llama a `Computador.bloquearPorSemaforo`: P sale de la CPU y pasa a `colaBloqueados` con su motivo.
3. Otro proceso Q (computador 2) hace `semSignal`. `SemaforoSO.semSignal` saca a P de la cola y se lo devuelve.
4. **El computador 2 no toca las colas del computador 1** (sería una carrera de hilos): llama a `computador1.recibirDespertar(P)`, que deja el aviso en la bandeja `despertares` bajo `candadoDespertares`.
5. Al comienzo de su siguiente ciclo, el computador 1 vacía su bandeja y pasa a P a Listos.

Si P muere por deadline mientras espera, `ProgramaProductorConsumidor.alTerminar` lo saca de la cola del semáforo y devuelve lo que tuviera tomado (hueco, mutex o elemento) para que el buffer no quede atascado.

## 7. Reglas para modificar el código sin romper la concurrencia

- Dentro de un `tic`, un computador solo toca **sus propias** colas, CPU, RAM y procesos. Para actuar sobre otro computador, usar su bandeja (`recibirDespertar`) o un `SemaforoSO`/`BufferSO`.
- Desde la interfaz, todo cambio sobre el cluster va entre `cluster.adquirir()` y `cluster.liberar()`. El cerrojo **no es reentrante**: no tomarlo dos veces seguidas.
- Nunca esperar a la interfaz (`invokeAndWait`) con el cerrojo tomado: la interfaz podría estar esperando ese mismo cerrojo. El reloj usa `invokeLater`.
- No usar colecciones de `java.util`: usar las estructuras de `avilaos.estructuras` y arreglos.
- Los `semWait` del productor y del consumidor deben mantener su orden (`vacios`/`llenos` primero y `mutex` después): invertirlo causa interbloqueo.

## 8. Cómo probar

`avilaos.pruebas.EjecutarPruebas` corre las 363 pruebas (en NetBeans: clic derecho y *Run File*). `avilaos.pruebas.ExperimentoPoliticas` genera las tablas de `docs/resultados_experimentos.md`.

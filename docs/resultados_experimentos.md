# Resultados de los experimentos

Generado con `java avilaos.pruebas.ExperimentoPoliticas`. Todo es determinista (simulacion sin hilos, ciclo a ciclo), asi que repetirlo da exactamente los mismos numeros. Las metricas se definen en `avilaos.metricas.ResumenMetricas`.

## Experimento 1: las cuatro politicas con la misma carga

Un computador, 8 procesos de CPU (instrucciones 8,3,6,2,5,4,7,1) que llegan entre los ciclos 0 y 5, con prioridades y deadlines distintos (algunos muy ajustados). Round Robin con quantum 3.

| Politica | Terminan todos | Throughput | Respuesta prom. | Cumplimiento | Equidad | Cambios de proceso |
|---|---|---|---|---|---|---|
| FCFS | ciclo 26 | 0.15 | 8.80 | 50% (4 de 8) | 0.38 | 0 |
| EDF | ciclo 36 | 0.22 | 9.63 | 100% (8 de 8) | 0.75 | 0 |
| ROUND_ROBIN | ciclo 31 | 0.16 | 7.00 | 63% (5 de 8) | 0.65 | 5 |
| PRIORIDADES | ciclo 32 | 0.19 | 9.50 | 75% (6 de 8) | 0.65 | 2 |

Procesos que vencen su deadline, por politica:

- FCFS: P2, P4, P6, P8
- EDF: ninguno
- ROUND_ROBIN: P4, P6, P8
- PRIORIDADES: P2, P5

## Experimento 2: efecto del quantum en Round Robin

| Quantum | Respuesta prom. | Cumplimiento | Equidad | Cambios de proceso |
|---|---|---|---|---|
| 1 | 3.14 | 50% | 0.84 | 25 |
| 2 | 5.14 | 50% | 0.83 | 11 |
| 3 | 7.00 | 63% | 0.65 | 5 |
| 5 | 7.80 | 63% | 0.61 | 3 |
| 8 | 8.80 | 50% | 0.38 | 0 |

## Experimento 3: productor y consumidor locales y remotos

Un productor y un consumidor de 12 elementos, buffer de capacidad 2. En el caso local los dos estan en el mismo computador que el buffer; en el remoto, el consumidor esta en otro.

| Caso | Latencia de red | Terminan en el ciclo | Bloqueo en semaforos (prom. ciclos) | Utilizacion CPU del sistema |
|---|---|---|---|---|
| Local | - | 144 | 20.50 | 50% |
| Remoto | 0 | 76 | 2.00 | 95% |
| Remoto | 2 | 112 | 18.00 | 70% |
| Remoto | 5 | 148 | 34.50 | 53% |
| Remoto | 10 | 208 | 62.00 | 38% |

## Experimento 4: capacidad del buffer

El mismo productor y consumidor locales (12 elementos) con buffers de distinta capacidad.

| Capacidad | Terminan en el ciclo | Bloqueo en semaforos (prom. ciclos) |
|---|---|---|
| 1 | 144 | 44.50 |
| 2 | 144 | 20.50 |
| 4 | 144 | 8.50 |
| 8 | 144 | 4.50 |


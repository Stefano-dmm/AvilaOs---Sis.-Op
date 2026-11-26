# Experimento: comparación de las políticas de planificación del disco

Se ejecuta con `avilaos.pruebas.ExperimentoDisco` (sin interfaz ni hilos). Cada corrida encola todas las solicitudes a la vez,
avanza el reloj del disco hasta vaciarlo y mide el desplazamiento del cabezal, la espera promedio en la cola y los ciclos totales.

**Configuración:** disco de 200 bloques (0 a 199), cabezal inicial en el bloque 50, velocidad de 5 posiciones por ciclo,
1 ciclo por bloque transferido. En C-SCAN el desplazamiento **no** cuenta el salto de regreso del 199 al 0.
Las colas aleatorias usan un generador congruencial con semillas fijas (1 a 20), así el experimento se repite igual.

## 1. Casos P1 a P4 del enunciado

Cola de llegada: 95, 180, 34, 119, 11, 123, 62, 64.

| Política | Orden de atención | Desplazamiento (bloques) | Espera promedio (ciclos) | Ciclos totales |
|---|---|---:|---:|---:|
| FIFO | 95,180,34,119,11,123,62,64 | 644 | 67.00 | 140 |
| SSTF | 62,64,34,11,95,119,123,180 | 236 | 20.88 | 58 |
| SCAN | 62,64,95,119,123,180,34,11 | 337 | 21.75 | 79 |
| C-SCAN | 62,64,95,119,123,180,11,34 | 183 | 18.00 | 49 |

## 2. Ejemplo del anexo del enunciado

Cola de llegada: 82, 17, 43, 140, 24, 16, 190. Los desplazamientos (518, 208, 332 y 192) coinciden con los del anexo.

| Política | Orden de atención | Desplazamiento (bloques) | Espera promedio (ciclos) | Ciclos totales |
|---|---|---:|---:|---:|
| FIFO | 82,17,43,140,24,16,190 | 518 | 37.43 | 114 |
| SSTF | 43,24,17,16,82,140,190 | 208 | 14.86 | 52 |
| SCAN | 82,140,190,43,24,17,16 | 332 | 39.29 | 77 |
| C-SCAN | 82,140,190,16,17,24,43 | 192 | 26.43 | 49 |

## 3. Colas aleatorias (promedio de 20 colas por tamaño)

| Solicitudes | Política | Desplazamiento promedio | Espera promedio (ciclos) | Ciclos totales promedio |
|---:|---|---:|---:|---:|
| 8 | FIFO | 547.0 | 52.79 | 120.4 |
| 8 | SSTF | 210.7 | 20.44 | 53.6 |
| 8 | SCAN | 310.9 | 24.33 | 73.8 |
| 8 | C-SCAN | 174.3 | 20.09 | 46.5 |
| 16 | FIFO | 1082.8 | 114.64 | 239.1 |
| 16 | SSTF | 250.9 | 30.31 | 72.9 |
| 16 | SCAN | 337.8 | 33.83 | 90.5 |
| 16 | C-SCAN | 184.7 | 28.07 | 59.9 |
| 32 | FIFO | 2172.0 | 232.27 | 479.2 |
| 32 | SSTF | 288.6 | 45.05 | 103.3 |
| 32 | SCAN | 341.3 | 46.45 | 113.9 |
| 32 | C-SCAN | 191.1 | 40.51 | 83.9 |
| 64 | FIFO | 4280.9 | 469.75 | 945.7 |
| 64 | SSTF | 307.1 | 69.59 | 153.9 |
| 64 | SCAN | 344.7 | 70.31 | 161.4 |
| 64 | C-SCAN | 194.6 | 64.04 | 131.5 |

## Conclusiones

- **FIFO** es la peor y empeora sin freno: el desplazamiento crece casi lineal con la cola (547 con 8 solicitudes, 4281 con 64)
  porque el cabezal salta de un extremo a otro siguiendo el orden de llegada. Su ventaja es que es justa: nadie espera más
  que los que llegaron antes.
- **SSTF** recorre muy poco (208 en el anexo, 307 con 64 solicitudes) y tiene buena espera promedio, pero puede dejar
  **inanición**: una solicitud lejana del cabezal se pospone mientras sigan llegando solicitudes cercanas.
- **SCAN** barre hasta el extremo y regresa. Recorre más que SSTF (332 contra 208 en el anexo) porque llega al extremo
  aunque no haya nadie allí, pero su espera máxima está acotada: ninguna solicitud espera más de un barrido completo.
- **C-SCAN** da el menor desplazamiento medido y la espera más uniforme, pero **su cifra es optimista**: el salto de
  regreso no se cuenta. Si se sumara (unos 199 bloques), en el anexo pasaría de 192 a unos 391, peor que SSTF y SCAN.
  Su ventaja real es el tiempo de espera parejo, porque las solicitudes de los bordes no se castigan.
- Con colas pequeñas (8) las cuatro políticas se parecen en espera, salvo FIFO. La diferencia se nota cuando la cola crece:
  con 64 solicitudes SSTF, SCAN y C-SCAN quedan cerca entre sí y FIFO queda diez veces peor.
- **Recomendación:** SSTF si lo importante es el rendimiento y la carga es ligera; SCAN o C-SCAN si hay carga alta y se quiere
  evitar la inanición; FIFO solo si la justicia estricta pesa más que el rendimiento.

## Efecto de la suspensión de procesos

La suspensión también usa el disco: cada swap-out y swap-in es una solicitud más en la cola de E/S. Con el área de
intercambio llena o muchas suspensiones, la cola crece y suben las esperas de todos los procesos bloqueados por E/S,
sobre todo con FIFO. Esto es razonamiento a partir del diseño, no una medición: con SCAN o C-SCAN las solicitudes de
swap (bloques 200 en adelante) deberían agruparse en el barrido hacia los bloques mayores en vez de forzar viajes largos.
Las suspensiones y reactivaciones de una corrida real se leen en la pestaña "Métricas disco" de la aplicación.

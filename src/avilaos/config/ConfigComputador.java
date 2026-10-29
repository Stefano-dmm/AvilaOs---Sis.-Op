package avilaos.config;

import avilaos.modelo.PoliticaPlanificacion;

/** Configuracion de un computador: nombre, RAM, politica inicial y quantum. */
public record ConfigComputador(String nombre, int ram, PoliticaPlanificacion politica, int quantum) {
}

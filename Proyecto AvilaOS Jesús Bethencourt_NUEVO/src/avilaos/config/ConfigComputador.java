package avilaos.config;

import avilaos.modelo.PoliticaPlanificacion;

public record ConfigComputador(String nombre, int ram, PoliticaPlanificacion politica, int quantum) {
}

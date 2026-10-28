package edu.upb.semillero.repository.dto.response.torneos;

import java.util.List;

/**
 * HU 11 - La tabla de posiciones de una categoría, o de un grupo si el torneo
 * se juega por grupos (tarea S3-14). No se guarda: se calcula desde los
 * partidos cerrados cada vez que se pide.
 *
 * <p>{@code desempates} son los códigos de los criterios en el orden del
 * reglamento, que es el orden en que se aplicaron.
 */
public record TablaPosicionesResponseDto(
        Long torneoCategoriaId,
        Long grupoId,
        String grupoNombre,
        List<String> desempates,
        int partidosCerrados,
        List<FilaTablaResponseDto> filas
) {}

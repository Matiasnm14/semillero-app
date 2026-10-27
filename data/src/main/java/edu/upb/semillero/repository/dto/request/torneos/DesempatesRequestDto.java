package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * Tarea S1-13: reemplaza el orden de los criterios de desempate. El orden de la
 * lista es el orden en que se aplican.
 *
 * <p>{@code usuarioId} es quien hace el cambio, para el historial de la S1-14.
 */
public record DesempatesRequestDto(
        @NotEmpty List<@NotBlank String> codigos,
        @NotNull @Positive Long usuarioId
) {}

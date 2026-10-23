package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Tarea S1-13: reemplaza el orden de los criterios de desempate. El orden de la
 * lista es el orden en que se aplican.
 */
public record DesempatesRequestDto(
        @NotEmpty List<@NotBlank String> codigos
) {}

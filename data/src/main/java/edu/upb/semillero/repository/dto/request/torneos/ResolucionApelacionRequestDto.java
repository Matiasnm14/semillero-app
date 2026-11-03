package edu.upb.semillero.repository.dto.request.torneos;

import edu.upb.semillero.repository.enums.torneos.EstadoApelacion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * HU 13 - Resolver una apelación (tarea S4-05). ACEPTADA anula la sanción,
 * RECHAZADA la deja como está y REDUCIDA la deja en {@code fechasResueltas},
 * que es obligatorio en ese caso.
 */
public record ResolucionApelacionRequestDto(
        @NotNull EstadoApelacion estado,
        @NotBlank @Size(max = 255) String resolucion,
        @Min(1) @Max(99) Short fechasResueltas,
        @NotNull @Positive Long usuarioId
) {}

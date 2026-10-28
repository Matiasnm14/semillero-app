package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * HU 5 - Lista de buena fe (tarea S2-06). El equipo va en la URL, porque la
 * lista cuelga de la inscripción.
 *
 * <p>Del jugador solo viaja su ID: los datos personales son de ficha técnica y
 * torneos no los copia. La posición es opcional, igual que la columna
 * {@code posiciones_id}.
 */
public record ListaBuenaFeRequestDto(
        @NotNull @Positive Long jugadorId,
        @Positive Long posicionId,
        @NotNull @Min(1) @Max(99) Short dorsal
) {}

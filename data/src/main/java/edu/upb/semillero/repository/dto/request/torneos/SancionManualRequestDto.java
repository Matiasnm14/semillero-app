package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * HU 13 - Sanción manual (tarea S4-05): la pone el organizador, con motivo
 * obligatorio ({@code chk_sancion_manual_motivo}). {@code partidoId} es
 * opcional: el partido donde pasó lo que se sanciona.
 */
public record SancionManualRequestDto(
        @NotNull @Positive Long jugadorId,
        @NotNull @Min(1) @Max(99) Short fechasSuspension,
        @NotBlank @Size(max = 255) String motivo,
        @Positive Long partidoId,
        @NotNull @Positive Long usuarioId
) {}

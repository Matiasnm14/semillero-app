package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * HU 13 - Presentar una apelación contra una sanción (tarea S4-05). La
 * presenta el entrenador del equipo, o el organizador.
 */
public record ApelacionRequestDto(
        @NotBlank @Size(max = 255) String motivo,
        @NotNull @Positive Long usuarioId
) {}

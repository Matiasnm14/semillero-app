package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Un integrante del cuerpo técnico presente en el partido (tarea S3-03).
 * {@code usuarioId} es opcional: el ayudante puede no tener cuenta.
 */
public record CuerpoTecnicoRequestDto(
        @NotNull @Positive Long clubId,
        @NotBlank @Size(max = 100) String nombreCompleto,
        @NotBlank @Size(max = 30) String cargo,
        @Positive Long usuarioId
) {}

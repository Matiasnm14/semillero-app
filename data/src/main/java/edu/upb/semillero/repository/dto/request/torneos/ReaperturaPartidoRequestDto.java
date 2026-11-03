package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * HU 10 - Reabrir un partido cerrado (tarea S3-10). Solo el organizador, y
 * queda en {@code auditoria} con el motivo.
 */
public record ReaperturaPartidoRequestDto(
        @NotBlank @Size(max = 255) String motivo,
        @NotNull @Positive Long usuarioId
) {}

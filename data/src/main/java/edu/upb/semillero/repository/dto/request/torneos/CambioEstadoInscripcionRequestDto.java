package edu.upb.semillero.repository.dto.request.torneos;

import edu.upb.semillero.repository.enums.torneos.EstadoInscripcion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * HU 6 - Aprobar o rechazar inscripciones (tarea S2-11). Observar y rechazar
 * exigen motivo, y el cambio queda en el historial con quién lo hizo.
 *
 * <p>{@code usuarioId} es el organizador que resuelve; viaja en el cuerpo
 * mientras no haya autenticación.
 */
public record CambioEstadoInscripcionRequestDto(
        @NotNull EstadoInscripcion estado,
        @Size(max = 255) String motivo,
        @NotNull @Positive Long usuarioId
) {}

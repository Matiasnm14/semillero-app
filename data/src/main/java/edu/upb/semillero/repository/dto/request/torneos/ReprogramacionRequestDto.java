package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * HU 8 - Reprogramar un partido (tareas S3-16 a S3-19).
 *
 * <p>A diferencia del ajuste del fixture (tarea S2-17), el motivo es
 * obligatorio, el cambio queda en {@code reprogramaciones} y se avisa a los dos
 * equipos. La cancha también es obligatoria porque
 * {@code reprogramaciones.canchas_nueva_id} es NOT NULL; si el partido sigue en
 * la misma cancha, se manda la misma.
 *
 * <p>{@code duracionPartidoMin} se puede omitir: el partido conserva la
 * duración que ya tenía.
 */
public record ReprogramacionRequestDto(
        @NotNull LocalDateTime fechaInicio,
        @NotNull @Positive Long canchaId,
        @Positive Integer duracionPartidoMin,
        @NotBlank @Size(max = 255) String motivo,
        @NotNull @Positive Long usuarioId
) {}

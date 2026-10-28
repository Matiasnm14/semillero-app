package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * HU 7 - Ajuste manual del fixture (tarea S2-17): mover un partido de día, de
 * hora o de cancha antes de que empiece, con la misma validación de choques
 * que usa el generador.
 *
 * <p>No es la reprogramación de la HU 8 (tarea S3-16), que exige motivo,
 * notifica a los dos equipos y guarda historial.
 */
public record AjustePartidoRequestDto(
        @NotNull LocalDateTime fechaInicio,
        @Positive Integer duracionPartidoMin,
        @Positive Long canchaId,
        @Size(max = 100) String arbitro,
        @Positive Long mesaControlId,
        @NotNull @Positive Long usuarioId
) {}

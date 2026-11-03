package edu.upb.semillero.repository.dto.request.torneos;

import edu.upb.semillero.repository.enums.torneos.TipoEventoPartido;
import edu.upb.semillero.repository.enums.torneos.TipoGol;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * HU 10 - Un evento del partido (tareas S3-06 y S3-07). Los jugadores van por
 * su ID; tienen que estar en la planilla.
 *
 * <ul>
 *   <li>GOL: {@code jugadorId} es el autor, {@code jugadorRelacionadoId} la
 *       asistencia (opcional) y {@code tipoGol} es obligatorio. En un AUTOGOL
 *       el autor es el que lo hizo en su arco y el gol suma al rival.</li>
 *   <li>AMARILLA y ROJA: {@code jugadorId} es el amonestado o expulsado, y
 *       {@code motivo} es obligatorio.</li>
 *   <li>CAMBIO: {@code jugadorId} sale y {@code jugadorRelacionadoId}
 *       entra.</li>
 * </ul>
 *
 * <p>{@code usuarioId} es la mesa de control que lo registra.
 */
public record EventoPartidoRequestDto(
        @NotNull TipoEventoPartido tipo,
        @NotNull @Min(0) @Max(130) Short minuto,
        @NotNull @Positive Long jugadorId,
        @Positive Long jugadorRelacionadoId,
        TipoGol tipoGol,
        @Size(max = 150) String motivo,
        @NotNull @Positive Long usuarioId
) {}

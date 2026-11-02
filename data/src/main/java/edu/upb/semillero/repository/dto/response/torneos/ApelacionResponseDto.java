package edu.upb.semillero.repository.dto.response.torneos;

import edu.upb.semillero.repository.enums.torneos.EstadoApelacion;
import java.time.LocalDateTime;

/** Una apelación y, si ya se resolvió, su resolución (tarea S4-05). */
public record ApelacionResponseDto(
        Long id,
        Long sancionId,
        String motivo,
        EstadoApelacion estado,
        String resolucion,
        Short fechasResueltas,
        Long usuarioId,
        Long resueltoPorId,
        LocalDateTime fechaPresentacion,
        LocalDateTime fechaResolucion
) {}

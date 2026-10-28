package edu.upb.semillero.repository.dto.response.torneos;

import edu.upb.semillero.repository.enums.torneos.TipoEventoPartido;
import edu.upb.semillero.repository.enums.torneos.TipoGol;
import java.time.LocalDateTime;

/**
 * Un evento del partido. {@code clubId} es el equipo al que suma: en un
 * autogol, el rival del autor.
 */
public record EventoPartidoResponseDto(
        Long id,
        Long partidoId,
        TipoEventoPartido tipo,
        Short minuto,
        Long jugadorId,
        Long jugadorRelacionadoId,
        Long clubId,
        TipoGol tipoGol,
        String motivo,
        Long usuarioId,
        LocalDateTime fechaRegistro
) {}

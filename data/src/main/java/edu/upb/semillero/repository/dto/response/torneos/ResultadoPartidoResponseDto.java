package edu.upb.semillero.repository.dto.response.torneos;

import edu.upb.semillero.repository.enums.EstadoPartido;
import java.time.LocalDateTime;
import java.util.List;

/**
 * El partido cerrado (tarea S3-09) o reabierto (tarea S3-10): resultado,
 * quién lo cerró y los minutos de cada jugador.
 */
public record ResultadoPartidoResponseDto(
        Long partidoId,
        EstadoPartido estado,
        Long clubLocalId,
        Long clubVisitanteId,
        Short golesLocal,
        Short golesVisitante,
        Short penalesLocal,
        Short penalesVisitante,
        Long ganadorWalkoverId,
        LocalDateTime fechaCierre,
        Long cerradoPorId,
        List<MinutosJugadorResponseDto> jugadores
) {}

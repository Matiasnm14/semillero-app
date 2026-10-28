package edu.upb.semillero.repository.dto.response.torneos;

/** Tarea S3-08: los minutos de un jugador, como quedaron en participaciones_partido. */
public record MinutosJugadorResponseDto(
        Long participacionId,
        Long jugadorId,
        Long clubId,
        Boolean esTitular,
        Short minutoEntrada,
        Short minutoSalida,
        Short minutosJugados
) {}

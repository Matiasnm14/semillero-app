package edu.upb.semillero.repository.dto.response.torneos;

public record ComparacionJugadoresResponseDto(
        EstadisticasJugadorResponseDto jugadorA,
        EstadisticasJugadorResponseDto jugadorB
) {}

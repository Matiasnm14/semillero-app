package edu.upb.semillero.service.torneos;

public record EstadisticasJugadorDto(
        Long jugadorId,
        Long torneoId,
        int partidosJugados,
        int minutosJugados,
        int goles,
        int asistencias,
        int amarillas,
        int rojas
) {}

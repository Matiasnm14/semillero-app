package edu.upb.semillero.repository.dto.response.torneos;

import java.math.BigDecimal;

/** Estadísticas deportivas; los minutos incompletos y la calificación no disponible son null. */
public record EstadisticasJugadorResponseDto(
        Long jugadorId,
        Integer temporada,
        Long torneoId,
        long partidosJugados,
        long goles,
        long asistencias,
        Long minutosJugados,
        long minutosRegistrados,
        boolean minutosCompletos,
        Short dorsal,
        Long dorsalPartidoId,
        Long dorsalClubId,
        BigDecimal calificacionMedia
) {}

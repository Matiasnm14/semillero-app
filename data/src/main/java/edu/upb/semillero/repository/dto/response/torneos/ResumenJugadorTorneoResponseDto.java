package edu.upb.semillero.repository.dto.response.torneos;

/**
 * HU 12 - Resumen de un jugador en un torneo (tarea S4-12): partidos,
 * minutos, goles, asistencias y tarjetas. Los autogoles van aparte y no
 * suman a los goles.
 *
 * <p>No confundir con {@code EstadisticasJugadorResponseDto}, del comparador de
 * jugadores (otro módulo): aquel es por temporada y trae calificación; este es
 * el resumen de un jugador dentro de un torneo, con tarjetas.
 */
public record ResumenJugadorTorneoResponseDto(
        Long jugadorId,
        Long torneoId,
        int partidosJugados,
        int partidosTitular,
        int minutosJugados,
        int goles,
        int asistencias,
        int autogoles,
        int amarillas,
        int rojas
) {}

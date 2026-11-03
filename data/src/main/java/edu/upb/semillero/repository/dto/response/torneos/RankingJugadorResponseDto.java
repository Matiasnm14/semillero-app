package edu.upb.semillero.repository.dto.response.torneos;

/**
 * Una fila de la tabla de goleadores o de asistencias (tarea S4-11). El
 * jugador va por su ID: el nombre es dato personal de ficha técnica.
 */
public record RankingJugadorResponseDto(
        int posicion,
        Long jugadorId,
        Long clubId,
        String clubNombre,
        int cantidad,
        int partidosJugados
) {}

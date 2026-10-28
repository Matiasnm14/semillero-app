package edu.upb.semillero.repository.dto.response.torneos;

/**
 * Valla menos vencida (tarea S4-11): los goles que recibió cada equipo en los
 * partidos que jugó, sin contar los W.O. Ordena el promedio por partido.
 */
public record VallaResponseDto(
        int posicion,
        Long clubId,
        String clubNombre,
        int partidosJugados,
        int golesEnContra,
        double promedio,
        int vallasInvictas
) {}

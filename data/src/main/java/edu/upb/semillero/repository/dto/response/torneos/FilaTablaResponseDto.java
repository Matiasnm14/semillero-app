package edu.upb.semillero.repository.dto.response.torneos;

/**
 * Una fila de la tabla de posiciones (HU 11). {@code fairPlay} son los puntos
 * de disciplina: 1 por amarilla y 3 por roja, menos es mejor.
 */
public record FilaTablaResponseDto(
        int posicion,
        Long clubId,
        String clubNombre,
        int puntos,
        int partidosJugados,
        int ganados,
        int empatados,
        int perdidos,
        int golesFavor,
        int golesContra,
        int diferenciaGoles,
        int fairPlay
) {}

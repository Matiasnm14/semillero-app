package edu.upb.semillero.repository.dto.response.torneos;

/**
 * Fair play por equipo (tarea S4-11): 1 punto por amarilla y 3 por roja, el
 * mismo cálculo que el desempate de la tabla. Menos es mejor.
 */
public record FairPlayResponseDto(
        int posicion,
        Long clubId,
        String clubNombre,
        int partidosJugados,
        int amarillas,
        int rojas,
        int puntos
) {}

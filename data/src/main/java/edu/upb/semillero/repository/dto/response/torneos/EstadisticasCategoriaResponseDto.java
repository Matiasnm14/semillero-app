package edu.upb.semillero.repository.dto.response.torneos;

import java.util.List;

/**
 * HU 12 - Las tablas del torneo para una categoría (tarea S4-11). Se
 * calculan desde los partidos cerrados: un partido anulado no cuenta, y
 * cada cierre las deja al día.
 */
public record EstadisticasCategoriaResponseDto(
        Long torneoCategoriaId,
        int partidosCerrados,
        List<RankingJugadorResponseDto> goleadores,
        List<RankingJugadorResponseDto> asistencias,
        List<VallaResponseDto> vallasMenosVencidas,
        List<FairPlayResponseDto> fairPlay
) {}

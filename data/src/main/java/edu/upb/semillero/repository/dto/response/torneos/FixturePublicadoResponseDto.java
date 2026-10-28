package edu.upb.semillero.repository.dto.response.torneos;

import edu.upb.semillero.repository.enums.torneos.EstadoTorneo;

/**
 * HU 7 - Lo que devuelve publicar el fixture (tarea S2-18): el torneo pasa a
 * EN_CURSO y desde ahí el fixture es visible para los equipos y la app.
 */
public record FixturePublicadoResponseDto(
        Long torneoId,
        EstadoTorneo estado,
        long totalPartidos,
        long totalCategorias
) {}

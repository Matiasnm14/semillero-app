package edu.upb.semillero.repository.dto.response.torneos;

public record TorneoCategoriaResponseDto(
        Long id,
        Long torneoId,
        String nombre,
        Short edadMinima,
        Short edadMaxima,
        Short cupoMaximo,
        Short duracionTiempoMin,
        Short jugadoresEnCancha,
        Short maxSustituciones
) {}

package edu.upb.semillero.repository.dto.response;

import edu.upb.semillero.repository.enums.EstadoPartido;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PlayerMatchMetricResponseDto(
        Long id,
        Long jugadorId,
        String jugadorNombre,
        Long partidoId,
        LocalDateTime fechaInicio,
        EstadoPartido estadoPartido,
        BigDecimal distanciaTotal,
        BigDecimal velocidadPromedio,
        BigDecimal velocidadMaxima,
        BigDecimal minutosJugados
) {}

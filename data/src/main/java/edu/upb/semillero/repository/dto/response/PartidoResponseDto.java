package edu.upb.semillero.repository.dto.response;

import edu.upb.semillero.repository.enums.EstadoPartido;
import java.time.LocalDateTime;

public record PartidoResponseDto(
        Long id,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        Long clubLocalId,
        Long clubVisitanteId,
        EstadoPartido estado
) {}

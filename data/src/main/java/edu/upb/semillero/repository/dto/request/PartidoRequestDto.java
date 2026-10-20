package edu.upb.semillero.repository.dto.request;

import edu.upb.semillero.repository.enums.EstadoPartido;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record PartidoRequestDto(
        @NotNull LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        @NotNull @Positive Long clubLocalId,
        @NotNull @Positive Long clubVisitanteId,
        @NotNull EstadoPartido estado
) {}

package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record FichadoRequestDto(
        LocalDateTime fechaFichaje,
        LocalDateTime fechaFinalFichaje,
        @Positive Long jugadorId,
        @Positive Long clubId
) {}

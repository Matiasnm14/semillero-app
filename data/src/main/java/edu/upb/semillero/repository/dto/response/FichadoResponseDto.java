package edu.upb.semillero.repository.dto.response;

import java.time.LocalDateTime;

public record FichadoResponseDto(
        Long id,
        LocalDateTime fechaFichaje,
        LocalDateTime fechaFinalFichaje,
        Long jugadorId,
        Long clubId
) {}

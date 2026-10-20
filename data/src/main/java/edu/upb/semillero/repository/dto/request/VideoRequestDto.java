package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record VideoRequestDto(
        @Size(max = 100) String tituloVideo,
        @Size(max = 100) String linkVideo,
        @Positive Long jugadorId
) {}

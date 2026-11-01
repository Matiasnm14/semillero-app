package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record HighlightRequestDto(
        @NotNull @Positive Long jugadorId,
        @NotBlank @Size(max = 2048) String youtubeUrl,
        Boolean visible
) {}

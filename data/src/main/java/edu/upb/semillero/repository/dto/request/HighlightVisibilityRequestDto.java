package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.NotNull;

public record HighlightVisibilityRequestDto(@NotNull Boolean visible) {}

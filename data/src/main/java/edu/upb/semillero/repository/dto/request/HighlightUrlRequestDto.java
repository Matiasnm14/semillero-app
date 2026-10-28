package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HighlightUrlRequestDto(@NotBlank @Size(max = 2048) String youtubeUrl) {}

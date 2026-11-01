package edu.upb.semillero.repository.dto.response;

import java.time.LocalDateTime;

public record HighlightResponseDto(
        Long id,
        Long jugadorId,
        String youtubeUrl,
        String youtubeVideoId,
        boolean visible,
        LocalDateTime createdAt
) {}

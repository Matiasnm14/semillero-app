package edu.upb.semillero.repository.dto.response;

public record VideoResponseDto(
        Long id,
        String tituloVideo,
        String linkVideo,
        Long jugadorId
) {}

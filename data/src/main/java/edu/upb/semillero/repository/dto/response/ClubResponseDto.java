package edu.upb.semillero.repository.dto.response;

public record ClubResponseDto(
        Long id,
        String nombreClub,
        Long ciudadId,
        String fotoClub
) {}

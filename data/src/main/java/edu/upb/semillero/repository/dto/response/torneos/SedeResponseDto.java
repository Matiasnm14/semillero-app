package edu.upb.semillero.repository.dto.response.torneos;

public record SedeResponseDto(
        Long id,
        String nombre,
        String direccion,
        Long ciudadId
) {}

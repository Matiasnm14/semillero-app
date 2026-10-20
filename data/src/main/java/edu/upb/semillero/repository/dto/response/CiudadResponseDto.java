package edu.upb.semillero.repository.dto.response;

public record CiudadResponseDto(
        Long id,
        String nombreCiudad,
        Long paisId
) {}

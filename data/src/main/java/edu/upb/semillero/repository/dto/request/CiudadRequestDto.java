package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CiudadRequestDto(
        @Size(max = 30) String nombreCiudad,
        @Positive Long paisId
) {}

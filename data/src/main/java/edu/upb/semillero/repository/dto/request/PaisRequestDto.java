package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Size;

public record PaisRequestDto(
        @Size(max = 30) String nombrePais
) {}

package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Size;

public record PosicionRequestDto(
        @Size(max = 100) String posicion,
        @Size(max = 4) String codigoPosicion
) {}

package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SolicitudContactoRequestDto(
        @Positive Long jugadorId,
        @NotBlank @Size(max = 120) String asunto,
        @NotBlank @Size(max = 500) String mensaje
) {}

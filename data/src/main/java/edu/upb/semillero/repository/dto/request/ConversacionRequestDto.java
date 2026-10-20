package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ConversacionRequestDto(
        @Size(max = 255) String mensaje,
        @Positive Long usuarioEnviaId,
        @Positive Long usuarioRecibeId
) {}

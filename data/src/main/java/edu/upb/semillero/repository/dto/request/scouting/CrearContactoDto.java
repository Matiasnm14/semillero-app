package edu.upb.semillero.repository.dto.request.scouting;

import jakarta.validation.constraints.*;
public record CrearContactoDto(@NotNull @Positive Long clubId, @NotNull @Positive Long jugadorId,
        @NotBlank @Size(max = 1000) String motivo) {}

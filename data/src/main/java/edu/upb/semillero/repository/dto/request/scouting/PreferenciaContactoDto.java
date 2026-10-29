package edu.upb.semillero.repository.dto.request.scouting;

import jakarta.validation.constraints.*;
public record PreferenciaContactoDto(@NotNull Boolean visible, @NotNull Boolean aceptaSolicitudes,
        @NotNull @PositiveOrZero Long version) {}

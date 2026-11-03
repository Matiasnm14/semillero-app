package edu.upb.semillero.repository.dto.request.scouting;

import jakarta.validation.constraints.*;

public record DecisionPruebaDto(@NotBlank String estado, @NotNull @PositiveOrZero Long version) {}

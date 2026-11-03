package edu.upb.semillero.repository.dto.request.scouting;

import jakarta.validation.constraints.*;

public record EvaluacionPruebaDto(
        @NotBlank String asistencia,
        @NotBlank String resultado,
        @NotBlank @Size(max = 1000) String observaciones,
        @NotNull @PositiveOrZero Long version) {}

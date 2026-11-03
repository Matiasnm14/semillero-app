package edu.upb.semillero.repository.dto.request.scouting;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.Set;

public record ConvocatoriaPruebaDto(
        @NotNull @Positive Long clubId,
        @NotBlank @Size(max = 120) String titulo,
        @NotNull LocalDate fechaPrueba,
        @NotNull LocalTime horaPrueba,
        @NotBlank @Size(max = 240) String lugar,
        @NotBlank @Size(max = 80) String categoria,
        @NotBlank @Size(max = 2000) String descripcion,
        @Min(1) @Max(1000) int cupos,
        @NotNull Instant limiteInscripcion,
        @Min(18) @Max(100) Integer edadMin,
        @Min(18) @Max(100) Integer edadMax,
        @Size(max = 20) Set<@NotBlank @Size(max = 80) String> posiciones,
        @Size(max = 20) Set<@NotBlank @Size(max = 240) String> requisitos,
        String estado,
        @PositiveOrZero Long version) {}

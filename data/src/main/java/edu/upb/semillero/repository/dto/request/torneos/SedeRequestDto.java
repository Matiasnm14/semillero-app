package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SedeRequestDto(
        @NotBlank @Size(max = 80) String nombre,
        @NotBlank @Size(max = 150) String direccion,
        @NotNull @Positive Long ciudadId
) {}

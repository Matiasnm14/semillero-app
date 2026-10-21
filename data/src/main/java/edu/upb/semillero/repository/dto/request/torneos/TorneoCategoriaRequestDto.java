package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Categoría de un torneo con su rango de edad. El torneo no viene en el cuerpo:
 * lo da la ruta, o el torneo que se está creando.
 */
public record TorneoCategoriaRequestDto(
        @NotBlank @Size(max = 20) String nombre,
        @PositiveOrZero Short edadMinima,
        @NotNull @Positive Short edadMaxima,
        @NotNull @Min(2) Short cupoMaximo,
        @NotNull @Positive Short duracionTiempoMin,
        @NotNull @Positive Short jugadoresEnCancha,
        @PositiveOrZero Short maxSustituciones
) {}

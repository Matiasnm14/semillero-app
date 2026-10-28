package edu.upb.semillero.repository.dto.request.torneos;

import edu.upb.semillero.repository.enums.torneos.TipoSuperficie;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Tarea S1-17: las dimensiones, si es techada, si tiene iluminación y
 * vestuarios, y la descripción libre acompañan a la cancha.
 */
public record CanchaRequestDto(
        @NotNull @Positive Long sedeId,
        @NotBlank @Size(max = 50) String nombre,
        @NotNull TipoSuperficie tipoSuperficie,
        @Positive @Digits(integer = 4, fraction = 1) BigDecimal largoM,
        @Positive @Digits(integer = 4, fraction = 1) BigDecimal anchoM,
        Boolean techada,
        Boolean iluminacion,
        Boolean vestuarios,
        @Size(max = 255) String descripcion,
        Boolean activa
) {}

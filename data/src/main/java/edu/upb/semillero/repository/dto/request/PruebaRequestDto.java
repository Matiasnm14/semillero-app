package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.FutureOrPresent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import edu.upb.semillero.repository.enums.EstadoPrueba;

public record PruebaRequestDto(
        @NotBlank @Size(max = 120) String titulo,
        @NotNull @FutureOrPresent LocalDate fechaPrueba,
        LocalTime horaPrueba,
        @Size(max = 240) String lugar,
        @Size(max = 40) String categoria,
        @NotNull @Positive Integer cupos,
        String descripcion,
        String requisitos,
        List<String> posiciones,
        EstadoPrueba estado,
        // Solo ADMIN puede escoger explícitamente el club; las cuentas de club
        // siempre usan la identidad del token.
        @Positive Long clubId,
        @Positive Long usuarioClubId
) {}

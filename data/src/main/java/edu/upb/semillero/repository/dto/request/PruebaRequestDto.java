package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;

public record PruebaRequestDto(
        LocalDate fechaPrueba,
        LocalTime horaPrueba,
        @Positive Long clubId,
        @Positive Long usuarioClubId
) {}

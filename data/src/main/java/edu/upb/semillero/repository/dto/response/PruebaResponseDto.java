package edu.upb.semillero.repository.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

public record PruebaResponseDto(
        Long id,
        LocalDate fechaPrueba,
        LocalTime horaPrueba,
        Long clubId,
        Long usuarioClubId
) {}

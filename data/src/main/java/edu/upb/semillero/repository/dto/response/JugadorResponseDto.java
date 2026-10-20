package edu.upb.semillero.repository.dto.response;

import java.time.LocalDate;

public record JugadorResponseDto(
        Long id,
        String piernaHabil,
        LocalDate fechaNacimiento,
        Double altura,
        Double peso,
        String descripcionJugador,
        Long usuarioId
) {}

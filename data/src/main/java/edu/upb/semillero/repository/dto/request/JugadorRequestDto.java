package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record JugadorRequestDto(
        @Size(max = 1) String piernaHabil,
        LocalDate fechaNacimiento,
        @Positive Double altura,
        @Positive Double peso,
        @Size(max = 255) String descripcionJugador,
        @Positive Long usuarioId
) {}

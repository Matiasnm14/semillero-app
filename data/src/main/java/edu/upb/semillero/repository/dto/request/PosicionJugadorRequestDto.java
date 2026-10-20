package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Positive;

public record PosicionJugadorRequestDto(
        @Positive Long posicionId,
        @Positive Long jugadorId
) {}

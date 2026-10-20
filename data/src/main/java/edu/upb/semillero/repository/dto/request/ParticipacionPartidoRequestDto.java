package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record ParticipacionPartidoRequestDto(
        @NotNull @Positive Long partidoId,
        @NotNull @Positive Long jugadorId,
        @NotNull @Positive Long clubId,
        @NotNull Boolean esTitular,
        @PositiveOrZero Short dorsal,
        @PositiveOrZero Short minutoEntrada,
        @PositiveOrZero Short minutoSalida,
        @PositiveOrZero Double velocidadProm,
        @PositiveOrZero Double distanciaRecorrida
) {}

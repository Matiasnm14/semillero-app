package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UsuarioClubRequestDto(
        @NotNull @Positive Long usuarioId
) {}

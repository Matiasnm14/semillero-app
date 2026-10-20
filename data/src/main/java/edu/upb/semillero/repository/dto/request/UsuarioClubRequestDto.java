package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Positive;

public record UsuarioClubRequestDto(
        @Positive Long usuarioId
) {}

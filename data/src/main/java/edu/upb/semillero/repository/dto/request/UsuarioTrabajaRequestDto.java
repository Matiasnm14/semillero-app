package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UsuarioTrabajaRequestDto(
        @NotNull @Positive Long usuarioClubId,
        @Size(max = 2) String tipoUsuarioClub,
        @NotNull @Positive Long clubId
) {}

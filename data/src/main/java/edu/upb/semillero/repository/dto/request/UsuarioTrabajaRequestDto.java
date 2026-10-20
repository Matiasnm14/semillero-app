package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UsuarioTrabajaRequestDto(
        @Positive Long usuarioClubId,
        @Size(max = 2) String tipoUsuarioClub,
        @Positive Long clubId
) {}

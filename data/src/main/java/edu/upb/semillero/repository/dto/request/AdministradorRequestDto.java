package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Positive;

public record AdministradorRequestDto(
        @Positive Long usuarioId
) {}

package edu.upb.semillero.repository.dto.request;

import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record ClinicaPagoRequestDto(
        LocalDateTime fechaInicioClinica,
        LocalDateTime fechaFinClinica,
        @Positive Long clubId,
        @Positive Long usuarioClubId
) {}

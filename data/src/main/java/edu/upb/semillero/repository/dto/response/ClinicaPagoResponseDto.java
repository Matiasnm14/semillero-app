package edu.upb.semillero.repository.dto.response;

import java.time.LocalDateTime;

public record ClinicaPagoResponseDto(
        Long id,
        LocalDateTime fechaInicioClinica,
        LocalDateTime fechaFinClinica,
        Long clubId,
        Long usuarioClubId
) {}

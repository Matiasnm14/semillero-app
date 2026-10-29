package edu.upb.semillero.repository.dto.response.scouting;

import java.time.Instant;
public record PreferenciaActualDto(Long jugadorId, boolean visible, boolean aceptaSolicitudes,
        Instant actualizadaEn, long version) {}

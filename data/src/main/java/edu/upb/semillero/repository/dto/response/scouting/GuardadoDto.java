package edu.upb.semillero.repository.dto.response.scouting;

import java.time.Instant;
public record GuardadoDto(Long clubId, Long jugadorId, Instant creadoEn) {}

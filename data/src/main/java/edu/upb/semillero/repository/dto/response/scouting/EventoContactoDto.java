package edu.upb.semillero.repository.dto.response.scouting;

import edu.upb.semillero.repository.enums.scouting.EstadoContacto;
import java.time.Instant;
import java.util.UUID;
public record EventoContactoDto(UUID id, Long actorId, EstadoContacto anterior, EstadoContacto nuevo,
        String motivo, Instant fecha) {}

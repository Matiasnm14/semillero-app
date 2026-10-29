package edu.upb.semillero.repository.dto.response.scouting;

import edu.upb.semillero.repository.enums.scouting.EstadoContacto;
import java.time.Instant;
import java.util.UUID;
public record SolicitudContactoDto(UUID id, Long clubId, Long jugadorId, Long solicitanteId,
        String motivo, EstadoContacto estado, Instant creadaEn, Instant actualizadaEn, long version) {}

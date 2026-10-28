package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.FaseTorneo;
import java.time.LocalDateTime;

public record PartidoTorneoDto(
        Long partidoId,
        Long torneoCategoriaId,
        Long grupoId,
        FaseTorneo fase,
        Short jornada,
        Long clubLocalId,
        Long clubVisitanteId,
        LocalDateTime fechaInicio,
        Long canchaId,
        EstadoPartido estado,
        Short golesLocal,
        Short golesVisitante
) {}

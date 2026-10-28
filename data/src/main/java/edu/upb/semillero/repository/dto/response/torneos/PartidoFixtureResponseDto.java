package edu.upb.semillero.repository.dto.response.torneos;

import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.FaseTorneo;
import edu.upb.semillero.repository.enums.torneos.TipoSuperficie;
import java.time.LocalDateTime;

/**
 * Un partido del fixture. La superficie y la descripción de la cancha viajan
 * acá porque la HU 3 pide mostrarlas en el fixture y en el partido.
 */
public record PartidoFixtureResponseDto(
        Long id,
        Long torneoCategoriaId,
        Long grupoId,
        FaseTorneo fase,
        Short jornada,
        Long clubLocalId,
        Long clubVisitanteId,
        Long canchaId,
        String canchaNombre,
        TipoSuperficie canchaSuperficie,
        String canchaDescripcion,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        EstadoPartido estado,
        String arbitro,
        Long mesaControlId,
        Short golesLocal,
        Short golesVisitante,
        Long partidoSiguienteId
) {}

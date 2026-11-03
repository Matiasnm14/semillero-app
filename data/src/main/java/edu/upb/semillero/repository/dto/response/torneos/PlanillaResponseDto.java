package edu.upb.semillero.repository.dto.response.torneos;

import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.FaseTorneo;
import edu.upb.semillero.repository.enums.torneos.EstadoPlanilla;
import edu.upb.semillero.repository.enums.torneos.TipoSuperficie;
import java.time.LocalDateTime;

/**
 * HU 9 - La planilla del partido (tarea S3-01). Viene precargada con el
 * torneo, la categoría, la fecha y hora, la cancha con su superficie, los dos
 * equipos y el árbitro, aunque todavía no se haya guardado: en ese caso
 * {@code planillaId} y {@code estado} van nulos.
 */
public record PlanillaResponseDto(
        Long planillaId,
        EstadoPlanilla estado,
        Long partidoId,
        EstadoPartido estadoPartido,
        Long torneoId,
        String torneoNombre,
        Long torneoCategoriaId,
        String categoriaNombre,
        FaseTorneo fase,
        Short jornada,
        LocalDateTime fechaInicio,
        Long canchaId,
        String canchaNombre,
        TipoSuperficie canchaSuperficie,
        String canchaDescripcion,
        String arbitro,
        Long mesaControlId,
        Short jugadoresEnCancha,
        String observacionesArbitro,
        Long usuarioId,
        LocalDateTime fechaConfirmacion,
        EquipoPlanillaResponseDto local,
        EquipoPlanillaResponseDto visitante
) {}

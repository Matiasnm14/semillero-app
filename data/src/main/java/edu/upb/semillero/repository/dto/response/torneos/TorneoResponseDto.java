package edu.upb.semillero.repository.dto.response.torneos;

import edu.upb.semillero.repository.enums.torneos.AmbitoTorneo;
import edu.upb.semillero.repository.enums.torneos.EstadoTorneo;
import edu.upb.semillero.repository.enums.torneos.FormatoTorneo;
import edu.upb.semillero.repository.enums.torneos.TipoTorneo;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Las categorías del torneo se consultan en
 * {@code GET /api/v1/torneos/{id}/categorias}.
 */
public record TorneoResponseDto(
        Long id,
        String nombre,
        Short temporada,
        TipoTorneo tipo,
        AmbitoTorneo ambito,
        String organizadorExterno,
        FormatoTorneo formato,
        Boolean idaVuelta,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        LocalDate fechaLimiteInscripcion,
        EstadoTorneo estado,
        Long usuarioId,
        LocalDateTime fechaCreacion
) {}

package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * HU 10 - Cerrar el partido (tarea S3-09).
 *
 * <ul>
 *   <li>En un torneo interno los goles salen de los eventos y no se mandan.</li>
 *   <li>En un campeonato externo el rival no tiene planilla, así que
 *       {@code golesLocal} y {@code golesVisitante} son obligatorios.</li>
 *   <li>Los penales solo van en un partido de eliminación que terminó
 *       empatado, y ahí son obligatorios.</li>
 *   <li>{@code ganadorWalkoverId} cierra el partido por W.O. sin jugarlo: el
 *       ganador se lleva los goles de W.O. del reglamento.</li>
 * </ul>
 *
 * <p>{@code observacionesArbitro} reemplaza las de la planilla si se manda.
 */
public record CierrePartidoRequestDto(
        @Min(0) @Max(99) Short golesLocal,
        @Min(0) @Max(99) Short golesVisitante,
        @Min(0) @Max(99) Short penalesLocal,
        @Min(0) @Max(99) Short penalesVisitante,
        @Positive Long ganadorWalkoverId,
        @Size(max = 4000) String observacionesArbitro,
        @NotNull @Positive Long usuarioId
) {}

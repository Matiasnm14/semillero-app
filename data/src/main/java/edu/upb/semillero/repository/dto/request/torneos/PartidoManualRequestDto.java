package edu.upb.semillero.repository.dto.request.torneos;

import edu.upb.semillero.repository.enums.FaseTorneo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * HU 7 - Carga manual de un partido (tarea S2-17). Es la única forma de cargar
 * partidos en un campeonato externo, donde no se genera fixture: el rival se
 * registra antes en {@code clubes} con nombre y ciudad.
 *
 * <p>También sirve en un torneo interno para agregar un partido suelto al
 * fixture ya generado. La cancha es opcional porque en un campeonato externo
 * puede no ser de ninguna de nuestras sedes.
 */
public record PartidoManualRequestDto(
        @NotNull @Positive Long clubLocalId,
        @NotNull @Positive Long clubVisitanteId,
        @NotNull LocalDateTime fechaInicio,
        @Positive Integer duracionPartidoMin,
        @Positive Long canchaId,
        @Positive Long grupoId,
        FaseTorneo fase,
        @Positive Short jornada,
        @Size(max = 100) String arbitro,
        @Positive Long mesaControlId,
        @NotNull @Positive Long usuarioId
) {}

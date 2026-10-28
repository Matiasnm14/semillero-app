package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * HU 4 - Inscribir un equipo (tarea S1-20). El equipo es el club inscrito en
 * una categoría del torneo; no hay tabla equipos.
 *
 * <p>El estado no se recibe: la inscripción nace PENDIENTE y solo la cambia el
 * organizador en la HU 6. {@code usuarioId} es el entrenador que inscribe, y
 * viaja en el cuerpo porque todavía no hay autenticación (HU 16).
 */
public record InscripcionEquipoRequestDto(
        @NotNull @Positive Long torneoCategoriaId,
        @NotNull @Positive Long clubId,
        @NotNull @Positive Long usuarioId,
        @Positive Long grupoId,
        @Positive Short cabezaSerie
) {}

package edu.upb.semillero.repository.dto.response.torneos;

import java.time.LocalDateTime;

/**
 * HU 5 - Lista de buena fe (tarea S2-06). Solo IDs: ni nombres ni fechas de
 * nacimiento.
 *
 * <p>{@code habilitado} en false con su {@code motivoInhabilitacion} es el
 * jugador que está en la lista pero no puede jugar, por ejemplo por una sanción
 * vigente de otro torneo.
 */
public record ListaBuenaFeResponseDto(
        Long id,
        Long inscripcionEquipoId,
        Long torneoId,
        Long jugadorId,
        Long posicionId,
        Short dorsal,
        Boolean habilitado,
        String motivoInhabilitacion,
        LocalDateTime fechaAlta
) {}

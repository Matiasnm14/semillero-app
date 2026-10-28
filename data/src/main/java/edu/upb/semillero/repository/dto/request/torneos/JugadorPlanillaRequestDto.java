package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Un jugador en la planilla del partido (tarea S3-02). Sale de la lista de buena
 * fe de su equipo: el club y el dorsal se toman de ahí, no se mandan.
 */
public record JugadorPlanillaRequestDto(
        @NotNull @Positive Long jugadorId,
        @NotNull Boolean esTitular,
        Boolean capitan
) {}

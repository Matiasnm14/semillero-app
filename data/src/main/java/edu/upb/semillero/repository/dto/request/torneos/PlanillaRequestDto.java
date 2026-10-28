package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * HU 9 - Guardar la planilla del partido (tareas S3-02 y S3-03). Es un
 * reemplazo completo: los jugadores y el cuerpo técnico que no vienen se sacan.
 *
 * <p>Se puede guardar incompleta y retomar después: las reglas de completitud
 * (titulares y capitán de cada equipo) se exigen al confirmar.
 * {@code usuarioId} es la mesa de control que la llena.
 */
public record PlanillaRequestDto(
        @NotNull List<@Valid @NotNull JugadorPlanillaRequestDto> jugadores,
        List<@Valid @NotNull CuerpoTecnicoRequestDto> cuerpoTecnico,
        @Size(max = 4000) String observacionesArbitro,
        @NotNull @Positive Long usuarioId
) {}

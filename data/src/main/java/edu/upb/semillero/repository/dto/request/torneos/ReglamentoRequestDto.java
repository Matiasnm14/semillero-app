package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.List;

/**
 * HU 2 - Reglamento del torneo (tarea S1-12). El torneo viene por la ruta.
 *
 * <p>{@code desempates} son los códigos de {@code criterios_desempate} en el
 * orden en que se aplican; el orden de la lista es el orden del desempate
 * (tarea S1-13). {@code fechaCorte} por defecto es el 31/12 de la temporada.
 */
public record ReglamentoRequestDto(
        @PositiveOrZero Short puntosVictoria,
        @PositiveOrZero Short puntosEmpate,
        @PositiveOrZero Short puntosDerrota,
        @Positive Short umbralAmarillas,
        @Positive Short fechasSuspensionAmarillas,
        @Positive Short fechasSuspensionRoja,
        @PositiveOrZero Short golesWalkover,
        LocalDate fechaCorte,
        @NotNull @Positive Short minJugadores,
        @NotNull @Positive Short maxJugadores,
        @Positive Short descansoMinimoMin,
        @NotEmpty List<String> desempates,
        @NotNull @Positive Long usuarioId
) {}

package edu.upb.semillero.repository.dto.response.torneos;

import java.time.LocalDate;
import java.util.List;

public record ReglamentoResponseDto(
        Long id,
        Long torneoId,
        Short puntosVictoria,
        Short puntosEmpate,
        Short puntosDerrota,
        Short umbralAmarillas,
        Short fechasSuspensionAmarillas,
        Short fechasSuspensionRoja,
        Short golesWalkover,
        LocalDate fechaCorte,
        Short minJugadores,
        Short maxJugadores,
        Short descansoMinimoMin,
        List<String> desempates
) {}

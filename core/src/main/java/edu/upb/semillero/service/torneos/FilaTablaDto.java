package edu.upb.semillero.service.torneos;

/**
 * Una fila de la tabla de posiciones. No se guarda: se calcula desde los
 * partidos FINALIZADO de torneos internos (HU 11).
 */
public record FilaTablaDto(
        Long clubId,
        int posicion,
        int puntos,
        int partidosJugados,
        int ganados,
        int empatados,
        int perdidos,
        int golesFavor,
        int golesContra,
        int diferenciaGoles
) {}

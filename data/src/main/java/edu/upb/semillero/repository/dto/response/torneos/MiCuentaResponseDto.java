package edu.upb.semillero.repository.dto.response.torneos;

import java.util.List;
import java.util.Set;

/**
 * HU 14 - Lo primero que la app pide después del login (tarea S4-15): los
 * roles, los jugadores de la cuenta, si alguno está suspendido, sus próximos
 * partidos y cuántos avisos hay sin leer. Solo IDs, sin datos personales.
 */
public record MiCuentaResponseDto(
        Long usuarioId,
        Set<String> roles,
        List<Long> jugadorIds,
        boolean suspendido,
        List<SancionResponseDto> suspensionesVigentes,
        List<PartidoFixtureResponseDto> proximosPartidos,
        long notificacionesSinLeer
) {}

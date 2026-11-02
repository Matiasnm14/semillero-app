package edu.upb.semillero.repository.dto.response.torneos;

import edu.upb.semillero.repository.enums.torneos.EstadoSancion;
import edu.upb.semillero.repository.enums.torneos.OrigenSancion;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Una sanción (HU 13). El jugador va por su ID, sin datos personales.
 *
 * <p>{@code jornadasSuspendido} son las fechas del fixture de su equipo que
 * cubre la suspensión (tarea S4-04): las primeras {@code fechasSuspension}
 * que juega el equipo después del partido que la originó. Si el fixture
 * todavía no tiene esas fechas, la lista sale más corta.
 * {@code usuarioId} es nulo cuando la generó el sistema.
 */
public record SancionResponseDto(
        Long id,
        Long torneoId,
        Long torneoCategoriaId,
        Long inscripcionEquipoId,
        Long clubId,
        Long jugadorId,
        Long listaBuenaFeId,
        Long partidoId,
        Short jornadaOrigen,
        OrigenSancion origen,
        short fechasSuspension,
        short fechasCumplidas,
        int fechasPendientes,
        String motivo,
        EstadoSancion estado,
        boolean apelacionPendiente,
        List<Short> jornadasSuspendido,
        Long usuarioId,
        LocalDateTime fechaRegistro
) {}

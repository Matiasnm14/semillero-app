package edu.upb.semillero.repository.dto.response.torneos;

/**
 * Un jugador de la lista de buena fe visto desde la planilla del partido
 * (tareas S3-01 y S3-02). Si está suspendido o no habilitado aparece
 * {@code bloqueado}, con el motivo, y no se puede poner en la planilla.
 *
 * <p>{@code enPlanilla} dice si ya está cargado en
 * {@code participaciones_partido}; los minutos se llenan al cerrar el partido.
 */
public record JugadorPlanillaResponseDto(
        Long jugadorId,
        Long listaBuenaFeId,
        Short dorsal,
        Long posicionId,
        boolean bloqueado,
        String motivoBloqueo,
        boolean enPlanilla,
        Long participacionId,
        Boolean esTitular,
        Boolean capitan,
        Short minutoEntrada,
        Short minutoSalida,
        Short minutosJugados
) {}

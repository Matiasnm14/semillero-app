package edu.upb.semillero.repository.dto.response.torneos;

/**
 * HU 5 - Lo que devuelve confirmar la lista de buena fe (tarea S2-08): cuántos
 * jugadores quedaron y contra qué mínimo y máximo del reglamento se comparó.
 */
public record ListaBuenaFeConfirmacionResponseDto(
        Long inscripcionEquipoId,
        Long torneoId,
        long totalJugadores,
        Short minJugadores,
        Short maxJugadores,
        Boolean listaConfirmada
) {}

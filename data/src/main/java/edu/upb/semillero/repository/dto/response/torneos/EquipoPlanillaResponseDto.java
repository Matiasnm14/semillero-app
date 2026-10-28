package edu.upb.semillero.repository.dto.response.torneos;

import java.util.List;

/**
 * Un equipo dentro de la planilla: su lista de buena fe con quién está en la
 * planilla, y su cuerpo técnico. En un campeonato externo el rival no tiene
 * inscripción ni lista, así que {@code inscripcionEquipoId} va nulo y la lista
 * vacía.
 */
public record EquipoPlanillaResponseDto(
        Long clubId,
        String clubNombre,
        Long inscripcionEquipoId,
        int titulares,
        int suplentes,
        List<JugadorPlanillaResponseDto> jugadores,
        List<CuerpoTecnicoResponseDto> cuerpoTecnico
) {}

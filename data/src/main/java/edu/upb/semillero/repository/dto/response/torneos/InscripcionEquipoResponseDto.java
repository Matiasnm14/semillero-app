package edu.upb.semillero.repository.dto.response.torneos;

import edu.upb.semillero.repository.enums.torneos.EstadoInscripcion;
import java.time.LocalDateTime;

public record InscripcionEquipoResponseDto(
        Long id,
        Long torneoCategoriaId,
        Long clubId,
        Long grupoId,
        Short cabezaSerie,
        EstadoInscripcion estado,
        String motivo,
        Boolean listaConfirmada,
        Long usuarioId,
        LocalDateTime fechaInscripcion
) {}

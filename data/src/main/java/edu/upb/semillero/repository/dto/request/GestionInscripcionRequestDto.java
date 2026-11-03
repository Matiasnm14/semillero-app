package edu.upb.semillero.repository.dto.request;

import edu.upb.semillero.repository.enums.EstadoInscripcionPrueba;
import jakarta.validation.constraints.Size;

public record GestionInscripcionRequestDto(
        EstadoInscripcionPrueba estado,
        @Size(max = 500) String notaClub
) {}

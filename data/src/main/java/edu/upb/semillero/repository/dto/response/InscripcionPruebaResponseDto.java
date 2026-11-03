package edu.upb.semillero.repository.dto.response;

import edu.upb.semillero.repository.enums.EstadoInscripcionPrueba;
import java.time.LocalDateTime;

public record InscripcionPruebaResponseDto(
        Long id, Long pruebaId, Long jugadorId, String nombreJugador,
        String fotoJugador, String piernaHabil, LocalDateTime fechaPostulacion,
        EstadoInscripcionPrueba estado, String nota, String notaClub
) {}

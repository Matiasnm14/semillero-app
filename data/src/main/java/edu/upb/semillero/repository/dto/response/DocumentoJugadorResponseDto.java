package edu.upb.semillero.repository.dto.response;

import edu.upb.semillero.repository.enums.EstadoDocumentoJugador;
import edu.upb.semillero.repository.enums.TipoDocumentoJugador;
import java.time.LocalDate;

public record DocumentoJugadorResponseDto(
        Long id,
        Long jugadorId,
        TipoDocumentoJugador tipo,
        String numero,
        LocalDate fechaEmision,
        LocalDate fechaVencimiento,
        EstadoDocumentoJugador estado
) {}

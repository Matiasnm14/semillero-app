package edu.upb.semillero.repository.dto.request;

import edu.upb.semillero.repository.enums.EstadoDocumentoJugador;
import edu.upb.semillero.repository.enums.TipoDocumentoJugador;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record DocumentoJugadorRequestDto(
        @NotNull Long jugadorId,
        @NotNull TipoDocumentoJugador tipo,
        @Size(max = 30) String numero,
        LocalDate fechaEmision,
        LocalDate fechaVencimiento,
        EstadoDocumentoJugador estado
) {}

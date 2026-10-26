package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.enums.torneos.EstadoSancion;
import edu.upb.semillero.repository.enums.torneos.OrigenSancion;

public record SancionDto(
        Long sancionId,
        Long jugadorId,
        Long torneoId,
        OrigenSancion origen,
        short fechasSuspension,
        short fechasCumplidas,
        EstadoSancion estado
) {}

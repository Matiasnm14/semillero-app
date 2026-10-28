package edu.upb.semillero.repository.dto.response.torneos;

import edu.upb.semillero.repository.enums.torneos.TipoSuperficie;
import java.math.BigDecimal;

public record CanchaResponseDto(
        Long id,
        Long sedeId,
        String nombre,
        TipoSuperficie tipoSuperficie,
        BigDecimal largoM,
        BigDecimal anchoM,
        Boolean techada,
        Boolean iluminacion,
        Boolean vestuarios,
        String descripcion,
        Boolean activa
) {}

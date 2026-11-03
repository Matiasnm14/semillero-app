package edu.upb.semillero.repository.dto.request;

import edu.upb.semillero.repository.enums.EstadoSolicitudContacto;
import jakarta.validation.constraints.Size;

public record RespuestaSolicitudRequestDto(
        EstadoSolicitudContacto estado,
        @Size(max = 500) String respuesta
) {}

package edu.upb.semillero.repository.dto.response.torneos;

import java.time.LocalDateTime;

/** Un aviso para la web o la app (tarea S4-16). */
public record NotificacionResponseDto(
        Long id,
        String modulo,
        String titulo,
        String mensaje,
        boolean leida,
        LocalDateTime fecha
) {}

package edu.upb.semillero.repository.dto.response;

import java.time.LocalDateTime;

public record NotificacionResponseDto(Long id, String modulo, String tipo, String titulo,
        String mensaje, boolean leida, LocalDateTime fecha, Long referenciaId,
        String referenciaTipo) {}

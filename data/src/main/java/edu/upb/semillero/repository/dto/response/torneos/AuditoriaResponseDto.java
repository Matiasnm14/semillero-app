package edu.upb.semillero.repository.dto.response.torneos;

import java.time.LocalDateTime;

/**
 * Una entrada del historial. Tarea S1-14.
 *
 * <p>{@code valorAnterior} y {@code valorNuevo} son el reglamento completo en
 * JSON, para poder comparar qué cambió entre dos entradas.
 */
public record AuditoriaResponseDto(
        Long id,
        String modulo,
        String tabla,
        Long registroId,
        String accion,
        String valorAnterior,
        String valorNuevo,
        String motivo,
        Long usuarioId,
        LocalDateTime fecha
) {}

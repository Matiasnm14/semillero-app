package edu.upb.semillero.repository.dto.response.torneos;

import java.time.LocalDateTime;

/**
 * Una entrada del historial de reprogramaciones de un partido (tarea S3-18).
 * {@code usuariosNotificados} es cuántos usuarios recibieron el aviso (tarea
 * S3-19); en el historial va {@code null}, porque el aviso no se guarda en la
 * reprogramación.
 */
public record ReprogramacionResponseDto(
        Long id,
        Long partidoId,
        LocalDateTime fechaInicioAnterior,
        Long canchaAnteriorId,
        LocalDateTime fechaInicioNueva,
        Long canchaNuevaId,
        String motivo,
        Long usuarioId,
        LocalDateTime fecha,
        Integer usuariosNotificados
) {}

package edu.upb.semillero.repository.dto.response.torneos;

/** Un integrante del cuerpo técnico presente en el partido (tarea S3-03). */
public record CuerpoTecnicoResponseDto(
        Long id,
        Long clubId,
        String nombreCompleto,
        String cargo,
        Long usuarioId
) {}

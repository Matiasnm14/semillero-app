package edu.upb.semillero.repository.dto.response.torneos;

public record ReglamentoDesempateResponseDto(
        Long id,
        Long criterioDesempateId,
        String codigo,
        String nombre,
        Short orden
) {}

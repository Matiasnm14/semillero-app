package edu.upb.semillero.repository.dto.response.torneos;

import java.time.LocalDateTime;

/**
 * Un partido cerrado, para el microservicio de video (punto 13 del acuerdo y
 * tarea S4-09): el mismo contrato mínimo que el aviso {@code PartidoFinalizado},
 * más la categoría para filtrar.
 */
public record PartidoCerradoResponseDto(
        Long partidoId,
        Long torneoCategoriaId,
        LocalDateTime fechaCierre
) {}

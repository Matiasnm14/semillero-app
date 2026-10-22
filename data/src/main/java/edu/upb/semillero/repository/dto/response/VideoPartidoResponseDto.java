package edu.upb.semillero.repository.dto.response;

import edu.upb.semillero.repository.enums.EstadoYoutube;
import java.time.Instant;

public record VideoPartidoResponseDto(
        Long id, Long partidoId, String nombreOriginal, String formato, Long tamanoBytes,
        Double duracionSegundos, String sha256, Instant fechaCarga, boolean disponibleParaAnalisis,
        String tituloYoutube, EstadoYoutube estadoYoutube, String youtubeId, String youtubeUrl,
        String errorYoutube) {
}

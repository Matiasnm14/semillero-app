package edu.upb.semillero.controller;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.response.VideoPartidoResponseDto;
import edu.upb.semillero.service.VideoPartidoService;
import edu.upb.semillero.service.exception.StorageUnavailableException;
import jakarta.validation.constraints.Positive;
import java.io.IOException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/partidos/{partidoId}/videos")
public class VideoPartidoController {
    private final VideoPartidoService service;

    public VideoPartidoController(VideoPartidoService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoPartidoResponseDto> create(@PathVariable @Positive Long partidoId,
            @RequestPart("file") MultipartFile file) {
        try (var input = file.getInputStream()) {
            var response = service.create(partidoId, file.getOriginalFilename(), file.getSize(), input);
            return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}").buildAndExpand(response.id()).toUri()).body(response);
        } catch (IOException e) {
            throw new StorageUnavailableException("No se pudo leer el archivo recibido");
        }
    }

    @GetMapping
    public PageResponse<VideoPartidoResponseDto> findAll(@PathVariable @Positive Long partidoId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return PageResponse.from(service.findAll(partidoId, pageable));
    }

    @GetMapping("/{id}")
    public VideoPartidoResponseDto findById(@PathVariable @Positive Long partidoId, @PathVariable @Positive Long id) {
        return service.findById(partidoId, id);
    }

    @PostMapping("/{id}/youtube/reintentar")
    public ResponseEntity<VideoPartidoResponseDto> retryYoutube(@PathVariable @Positive Long partidoId,
            @PathVariable @Positive Long id) {
        return ResponseEntity.accepted().body(service.retryYoutube(partidoId, id));
    }
}

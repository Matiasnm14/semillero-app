package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.dto.request.torneos.ReprogramacionRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.ReprogramacionResponseDto;
import edu.upb.semillero.service.torneos.ReprogramacionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * HU 8 - Reprogramar un partido (tareas S3-16 a S3-19). Solo el organizador:
 * la regla está en {@code SeguridadConfiguration}.
 */
@RestController
@RequestMapping("/api/v1/torneos/partidos/{partidoId}/reprogramaciones")
public class ReprogramacionController {
    private final ReprogramacionService service;

    public ReprogramacionController(ReprogramacionService service) {
        this.service = service;
    }

    /** Tarea S3-18: el historial de reprogramaciones del partido. */
    @GetMapping
    public List<ReprogramacionResponseDto> historial(@PathVariable @Positive Long partidoId) {
        return service.historial(partidoId);
    }

    @PostMapping
    public ResponseEntity<ReprogramacionResponseDto> reprogramar(@PathVariable @Positive Long partidoId,
            @Valid @RequestBody ReprogramacionRequestDto request) {
        ReprogramacionResponseDto response = service.reprogramar(partidoId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().build().toUri())
                .body(response);
    }
}

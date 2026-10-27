package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.dto.request.torneos.DesempatesRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.ReglamentoRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.AuditoriaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.ReglamentoDesempateResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.ReglamentoResponseDto;
import edu.upb.semillero.service.torneos.ReglamentoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/torneos/{torneoId}/reglamento")
public class ReglamentoController {
    private final ReglamentoService service;

    public ReglamentoController(ReglamentoService service) {
        this.service = service;
    }

    @GetMapping
    public ReglamentoResponseDto findByTorneo(@PathVariable @Positive Long torneoId) {
        return service.findByTorneo(torneoId);
    }

    @PostMapping
    public ResponseEntity<ReglamentoResponseDto> create(@PathVariable @Positive Long torneoId,
            @Valid @RequestBody ReglamentoRequestDto request) {
        ReglamentoResponseDto response = service.create(torneoId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().build().toUri())
                .body(response);
    }

    @PutMapping
    public ReglamentoResponseDto update(@PathVariable @Positive Long torneoId,
            @Valid @RequestBody ReglamentoRequestDto request) {
        return service.update(torneoId, request);
    }

    /** {@code usuarioId} va como parámetro porque un DELETE no lleva cuerpo, y la S1-14 pide registrar quién borró. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable @Positive Long torneoId,
            @RequestParam @Positive Long usuarioId) {
        service.delete(torneoId, usuarioId);
        return ResponseEntity.noContent().build();
    }

    /** Tarea S1-14: historial de cambios del reglamento, del más reciente al más antiguo. */
    @GetMapping("/historial")
    public List<AuditoriaResponseDto> findHistorial(@PathVariable @Positive Long torneoId) {
        return service.findHistorial(torneoId);
    }

    @GetMapping("/desempates")
    public List<ReglamentoDesempateResponseDto> findDesempates(@PathVariable @Positive Long torneoId) {
        return service.findDesempates(torneoId);
    }

    /** Tarea S1-13: el orden de la lista es el orden en que se aplican. */
    @PutMapping("/desempates")
    public List<ReglamentoDesempateResponseDto> reemplazarDesempates(@PathVariable @Positive Long torneoId,
            @Valid @RequestBody DesempatesRequestDto request) {
        return service.reemplazarDesempates(torneoId, request.codigos(), request.usuarioId());
    }
}

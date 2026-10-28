package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.request.torneos.CambioEstadoInscripcionRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.InscripcionEquipoRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.AuditoriaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.InscripcionEquipoResponseDto;
import edu.upb.semillero.repository.enums.torneos.EstadoInscripcion;
import edu.upb.semillero.service.torneos.InscripcionEquipoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/torneos/inscripciones")
public class InscripcionEquipoController {
    private final InscripcionEquipoService service;

    public InscripcionEquipoController(InscripcionEquipoService service) {
        this.service = service;
    }

    /** Tarea S2-10: el listado del organizador, filtrable por torneo, categoría y estado. */
    @GetMapping
    public PageResponse<InscripcionEquipoResponseDto> findAll(
            @RequestParam(required = false) @Positive Long torneoId,
            @RequestParam(required = false) @Positive Long torneoCategoriaId,
            @RequestParam(required = false) EstadoInscripcion estado,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return PageResponse.from(service.findAll(torneoId, torneoCategoriaId, estado, pageable));
    }

    @GetMapping("/{id}")
    public InscripcionEquipoResponseDto findById(@PathVariable @Positive Long id) {
        return service.findById(id);
    }

    /** Tarea S2-12: el historial de estados de una inscripción. */
    @GetMapping("/{id}/historial")
    public List<AuditoriaResponseDto> findHistorial(@PathVariable @Positive Long id) {
        return service.findHistorial(id);
    }

    @PostMapping
    public ResponseEntity<InscripcionEquipoResponseDto> create(
            @Valid @RequestBody InscripcionEquipoRequestDto request) {
        InscripcionEquipoResponseDto response = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(response.id()).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public InscripcionEquipoResponseDto update(@PathVariable @Positive Long id,
            @Valid @RequestBody InscripcionEquipoRequestDto request) {
        return service.update(id, request);
    }

    /** Tarea S2-11: aprobar, observar o rechazar, con motivo cuando corresponde. */
    @PutMapping("/{id}/estado")
    public InscripcionEquipoResponseDto cambiarEstado(@PathVariable @Positive Long id,
            @Valid @RequestBody CambioEstadoInscripcionRequestDto request) {
        return service.cambiarEstado(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

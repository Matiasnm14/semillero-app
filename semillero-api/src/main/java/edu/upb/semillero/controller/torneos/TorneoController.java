package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.request.torneos.TorneoRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.TorneoResponseDto;
import edu.upb.semillero.service.torneos.TorneoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/torneos")
public class TorneoController {
    private final TorneoService service;

    public TorneoController(TorneoService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TorneoResponseDto> findAll(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return PageResponse.from(service.findAll(pageable));
    }

    @GetMapping("/{id}")
    public TorneoResponseDto findById(@PathVariable @Positive Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<TorneoResponseDto> create(@Valid @RequestBody TorneoRequestDto request) {
        TorneoResponseDto response = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(response.id()).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public TorneoResponseDto update(@PathVariable @Positive Long id, @Valid @RequestBody TorneoRequestDto request) {
        return service.update(id, request);
    }

    /** Tarea S1-10: pasa el torneo de BORRADOR a INSCRIPCIONES_ABIERTAS. */
    @PostMapping("/{id}/abrir-inscripciones")
    public TorneoResponseDto abrirInscripciones(@PathVariable @Positive Long id) {
        return service.abrirInscripciones(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.request.torneos.TorneoCategoriaRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.TorneoCategoriaResponseDto;
import edu.upb.semillero.service.torneos.TorneoCategoriaService;
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
@RequestMapping("/api/v1/torneos/{torneoId}/categorias")
public class TorneoCategoriaController {
    private final TorneoCategoriaService service;

    public TorneoCategoriaController(TorneoCategoriaService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TorneoCategoriaResponseDto> findAll(@PathVariable @Positive Long torneoId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return PageResponse.from(service.findAllByTorneo(torneoId, pageable));
    }

    @GetMapping("/{id}")
    public TorneoCategoriaResponseDto findById(@PathVariable @Positive Long torneoId,
            @PathVariable @Positive Long id) {
        return service.findById(torneoId, id);
    }

    @PostMapping
    public ResponseEntity<TorneoCategoriaResponseDto> create(@PathVariable @Positive Long torneoId,
            @Valid @RequestBody TorneoCategoriaRequestDto request) {
        TorneoCategoriaResponseDto response = service.create(torneoId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(response.id()).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public TorneoCategoriaResponseDto update(@PathVariable @Positive Long torneoId,
            @PathVariable @Positive Long id, @Valid @RequestBody TorneoCategoriaRequestDto request) {
        return service.update(torneoId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long torneoId, @PathVariable @Positive Long id) {
        service.delete(torneoId, id);
        return ResponseEntity.noContent().build();
    }
}

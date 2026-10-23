package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.request.torneos.InscripcionEquipoRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.InscripcionEquipoResponseDto;
import edu.upb.semillero.service.torneos.InscripcionEquipoService;
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

    @GetMapping
    public PageResponse<InscripcionEquipoResponseDto> findAll(
            @RequestParam(required = false) @Positive Long torneoCategoriaId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return PageResponse.from(torneoCategoriaId == null
                ? service.findAll(pageable)
                : service.findAllByCategoria(torneoCategoriaId, pageable));
    }

    @GetMapping("/{id}")
    public InscripcionEquipoResponseDto findById(@PathVariable @Positive Long id) {
        return service.findById(id);
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

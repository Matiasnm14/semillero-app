package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.request.torneos.CanchaRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.CanchaResponseDto;
import edu.upb.semillero.service.torneos.CanchaService;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Torneos - HU 3", description = "Canchas con superficie y descripción")
@RestController
@RequestMapping("/api/v1/torneos/canchas")
public class CanchaController {
    private final CanchaService service;

    public CanchaController(CanchaService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<CanchaResponseDto> findAll(
            @RequestParam(required = false) @Positive Long sedeId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return PageResponse.from(sedeId == null
                ? service.findAll(pageable)
                : service.findAllBySede(sedeId, pageable));
    }

    @GetMapping("/{id}")
    public CanchaResponseDto findById(@PathVariable @Positive Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<CanchaResponseDto> create(@Valid @RequestBody CanchaRequestDto request) {
        CanchaResponseDto response = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(response.id()).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public CanchaResponseDto update(@PathVariable @Positive Long id, @Valid @RequestBody CanchaRequestDto request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

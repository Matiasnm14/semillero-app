package edu.upb.semillero.controller;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.request.AdministradorRequestDto;
import edu.upb.semillero.repository.dto.response.AdministradorResponseDto;
import edu.upb.semillero.service.AdministradorService;
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
@RequestMapping("/api/v1/administradores")
public class AdministradorController {
    private final AdministradorService service;

    public AdministradorController(AdministradorService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdministradorResponseDto> findAll(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return PageResponse.from(service.findAll(pageable));
    }

    @GetMapping("/{id}")
    public AdministradorResponseDto findById(@PathVariable @Positive Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<AdministradorResponseDto> create(@Valid @RequestBody AdministradorRequestDto request) {
        AdministradorResponseDto response = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(response.id()).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public AdministradorResponseDto update(@PathVariable @Positive Long id, @Valid @RequestBody AdministradorRequestDto request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

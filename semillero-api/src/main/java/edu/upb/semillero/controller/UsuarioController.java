package edu.upb.semillero.controller;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.request.UsuarioRequestDto;
import edu.upb.semillero.repository.dto.response.UsuarioResponseDto;
import edu.upb.semillero.service.UsuarioService;
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
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {
    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<UsuarioResponseDto> findAll(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return PageResponse.from(service.findAll(pageable));
    }

    @GetMapping("/{id}")
    public UsuarioResponseDto findById(@PathVariable @Positive Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<UsuarioResponseDto> create(@Valid @RequestBody UsuarioRequestDto request) {
        UsuarioResponseDto response = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(response.id()).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public UsuarioResponseDto update(@PathVariable @Positive Long id, @Valid @RequestBody UsuarioRequestDto request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

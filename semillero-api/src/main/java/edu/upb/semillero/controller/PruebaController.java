package edu.upb.semillero.controller;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.request.PruebaRequestDto;
import edu.upb.semillero.repository.dto.response.PruebaResponseDto;
import edu.upb.semillero.service.PruebaService;
import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/pruebas")
public class PruebaController {
    private final PruebaService service;

    public PruebaController(PruebaService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PruebaResponseDto> findAll(@RequestParam(required = false) @Positive Long clubId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return PageResponse.from(service.findAll(pageable, clubId));
    }

    @GetMapping("/{id}")
    public PruebaResponseDto findById(@PathVariable @Positive Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<PruebaResponseDto> create(@Valid @RequestBody PruebaRequestDto request,
            @AuthenticationPrincipal UsuarioAutenticado actor, Authentication authentication) {
        PruebaResponseDto response = service.create(request, actor(authentication, actor));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(response.id()).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public PruebaResponseDto update(@PathVariable @Positive Long id, @Valid @RequestBody PruebaRequestDto request,
            @AuthenticationPrincipal UsuarioAutenticado actor, Authentication authentication) {
        return service.update(id, request, actor(authentication, actor));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id,
            @AuthenticationPrincipal UsuarioAutenticado actor, Authentication authentication) {
        service.delete(id, actor(authentication, actor));
        return ResponseEntity.noContent().build();
    }

    private UsuarioAutenticado actor(Authentication authentication, UsuarioAutenticado principal) {
        if (principal != null) return principal;
        if (authentication == null) return null;
        var roles = authentication.getAuthorities().stream().map(a -> a.getAuthority().replaceFirst("^ROLE_", "")).toList();
        return new UsuarioAutenticado(null, "", roles, null);
    }
}

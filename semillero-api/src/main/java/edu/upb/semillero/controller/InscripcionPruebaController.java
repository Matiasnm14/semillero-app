package edu.upb.semillero.controller;

import edu.upb.semillero.repository.dto.request.GestionInscripcionRequestDto;
import edu.upb.semillero.repository.dto.request.InscripcionPruebaRequestDto;
import edu.upb.semillero.repository.dto.response.InscripcionPruebaResponseDto;
import edu.upb.semillero.service.InscripcionPruebaService;
import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/pruebas/{pruebaId}/inscripciones")
public class InscripcionPruebaController {
    private final InscripcionPruebaService service;

    public InscripcionPruebaController(InscripcionPruebaService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<InscripcionPruebaResponseDto> inscribir(@PathVariable @Positive Long pruebaId,
            @Valid @RequestBody(required = false) InscripcionPruebaRequestDto request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        InscripcionPruebaResponseDto response = service.inscribir(pruebaId, request, actor);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri()).body(response);
    }

    @GetMapping
    public List<InscripcionPruebaResponseDto> listar(@PathVariable @Positive Long pruebaId,
            @AuthenticationPrincipal UsuarioAutenticado actor, Authentication authentication) {
        return service.listar(pruebaId, actor(authentication, actor));
    }

    @PatchMapping("/{inscripcionId}")
    public InscripcionPruebaResponseDto gestionar(@PathVariable @Positive Long pruebaId,
            @PathVariable @Positive Long inscripcionId,
            @Valid @RequestBody GestionInscripcionRequestDto request,
            @AuthenticationPrincipal UsuarioAutenticado actor, Authentication authentication) {
        return service.gestionar(pruebaId, inscripcionId, request, actor(authentication, actor));
    }

    @DeleteMapping("/{inscripcionId}")
    public ResponseEntity<Void> retirar(@PathVariable @Positive Long pruebaId,
            @PathVariable @Positive Long inscripcionId,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        service.retirar(pruebaId, inscripcionId, actor);
        return ResponseEntity.noContent().build();
    }

    private UsuarioAutenticado actor(Authentication authentication, UsuarioAutenticado principal) {
        if (principal != null) return principal;
        if (authentication == null) return null;
        var roles = authentication.getAuthorities().stream().map(a -> a.getAuthority().replaceFirst("^ROLE_", "")).toList();
        return new UsuarioAutenticado(null, "", roles, null);
    }
}

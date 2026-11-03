package edu.upb.semillero.controller;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.request.RespuestaSolicitudRequestDto;
import edu.upb.semillero.repository.dto.request.SolicitudContactoRequestDto;
import edu.upb.semillero.repository.dto.response.SolicitudContactoResponseDto;
import edu.upb.semillero.service.SolicitudContactoService;
import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/solicitudes-contacto")
public class SolicitudContactoController {
    private final SolicitudContactoService service;

    public SolicitudContactoController(SolicitudContactoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SolicitudContactoResponseDto> crear(@Valid @RequestBody SolicitudContactoRequestDto request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        SolicitudContactoResponseDto response = service.crear(request, actor);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri()).body(response);
    }

    @GetMapping("/club/{clubId}")
    public PageResponse<SolicitudContactoResponseDto> delClub(@PathVariable @Positive Long clubId,
            @AuthenticationPrincipal UsuarioAutenticado actor,
            @PageableDefault(size = 20, sort = "fechaEnvio", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(service.delClub(clubId, actor, pageable));
    }

    @GetMapping("/jugador/{jugadorId}")
    public PageResponse<SolicitudContactoResponseDto> delJugador(@PathVariable @Positive Long jugadorId,
            @AuthenticationPrincipal UsuarioAutenticado actor,
            @PageableDefault(size = 20, sort = "fechaEnvio", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(service.delJugador(jugadorId, actor, pageable));
    }

    @PatchMapping("/{id}/estado")
    public SolicitudContactoResponseDto responder(@PathVariable @Positive Long id,
            @Valid @RequestBody RespuestaSolicitudRequestDto request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return service.responder(id, request, actor);
    }
}

package edu.upb.semillero.controller;

import edu.upb.semillero.controller.dto.NotificacionesPageResponse;
import edu.upb.semillero.repository.dto.response.NotificacionResponseDto;
import edu.upb.semillero.service.NotificacionService;
import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificaciones")
public class NotificacionController {
    private final NotificacionService service;

    public NotificacionController(NotificacionService service) {
        this.service = service;
    }

    @GetMapping
    public NotificacionesPageResponse listar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required = false) Boolean leida,
            @PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return NotificacionesPageResponse.from(service.listar(usuario.usuarioId(), leida, pageable),
                service.contarNoLeidas(usuario.usuarioId()));
    }

    @PatchMapping("/{id}/marcar-leida")
    public void marcarLeida(@PathVariable @Positive Long id,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.marcarLeida(id, usuario.usuarioId());
    }
}

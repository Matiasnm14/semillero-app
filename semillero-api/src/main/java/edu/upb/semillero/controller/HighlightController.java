package edu.upb.semillero.controller;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.request.HighlightRequestDto;
import edu.upb.semillero.repository.dto.request.HighlightUrlRequestDto;
import edu.upb.semillero.repository.dto.request.HighlightVisibilityRequestDto;
import edu.upb.semillero.repository.dto.response.HighlightResponseDto;
import edu.upb.semillero.service.AuthenticationService;
import edu.upb.semillero.service.VideoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/highlights")
public class HighlightController {
    private final VideoService service;
    private final AuthenticationService authentication;

    public HighlightController(VideoService service, AuthenticationService authentication) {
        this.service = service;
        this.authentication = authentication;
    }

    @GetMapping
    public PageResponse<HighlightResponseDto> findByPlayer(
            @RequestParam("jugadorId") @Positive Long jugadorId,
            @RequestParam(defaultValue = "false") boolean incluirOcultos,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        if (incluirOcultos) authentication.authorizeHiddenHighlightHistory(authorization, jugadorId);
        return PageResponse.from(service.findHighlights(jugadorId, incluirOcultos, pageable));
    }

    @PostMapping
    public ResponseEntity<HighlightResponseDto> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody HighlightRequestDto request) {
        authentication.authorizeHighlightManagement(authorization, request.jugadorId());
        HighlightResponseDto response = service.createHighlight(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(response.id()).toUri())
                .body(response);
    }

    @PatchMapping("/{id}")
    public HighlightResponseDto updateVisibility(@PathVariable @Positive Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody HighlightVisibilityRequestDto request) {
        authentication.authorizeHighlightManagement(authorization, service.highlightPlayerId(id));
        return service.updateHighlightVisibility(id, request.visible());
    }

    @PutMapping("/{id}")
    public HighlightResponseDto updateUrl(@PathVariable @Positive Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody HighlightUrlRequestDto request) {
        authentication.authorizeHighlightManagement(authorization, service.highlightPlayerId(id));
        return service.updateHighlightUrl(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authentication.authorizeHighlightManagement(authorization, service.highlightPlayerId(id));
        service.deleteHighlight(id);
        return ResponseEntity.noContent().build();
    }
}

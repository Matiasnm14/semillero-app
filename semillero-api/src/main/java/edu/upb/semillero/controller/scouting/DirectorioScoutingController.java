package edu.upb.semillero.controller.scouting;

import edu.upb.semillero.controller.dto.PageResponse;
import edu.upb.semillero.repository.dto.response.scouting.*;
import edu.upb.semillero.service.scouting.DirectorioScoutingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController @RequestMapping("/api/v1/scouting") @RequiredArgsConstructor
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "scoutingBearer")
@Tag(name = "Scouting - directorio", description = "Perfiles actualizados desde ficha técnica, sin datos privados")
public class DirectorioScoutingController {
    private final DirectorioScoutingService service;
    @GetMapping("/jugadores")
    public PageResponse<JugadorDirectorioDto> jugadores(@RequestParam(defaultValue = "") @Size(max = 80) String q,
            @RequestParam(required = false) @Positive Long posicionId, @RequestParam(required = false) @Positive Long clubId,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.jugadores(q, posicionId, clubId, page, size));
    }
    @GetMapping("/jugadores/{id}")
    public JugadorDirectorioDto jugador(@PathVariable @Positive Long id) { return service.jugador(id); }
    @GetMapping("/clubes")
    public PageResponse<ClubDirectorioDto> clubes(@RequestParam(defaultValue = "") @Size(max = 80) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.clubes(q, page, size));
    }
}

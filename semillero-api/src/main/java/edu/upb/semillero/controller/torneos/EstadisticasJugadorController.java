package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.dto.response.torneos.ComparacionJugadoresResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.EstadisticasJugadorResponseDto;
import edu.upb.semillero.service.torneos.EstadisticasJugadorService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/torneos/jugadores")
public class EstadisticasJugadorController {
    private final EstadisticasJugadorService service;

    public EstadisticasJugadorController(EstadisticasJugadorService service) {
        this.service = service;
    }

    @GetMapping("/{jugadorId}/estadisticas")
    public EstadisticasJugadorResponseDto forPlayer(@PathVariable @Positive Long jugadorId,
            @RequestParam(required = false) @Positive @Max(32767) Integer temporada,
            @RequestParam(required = false) @Positive Long torneoId) {
        return service.forPlayer(jugadorId, temporada, torneoId);
    }

    @GetMapping("/comparacion")
    public ComparacionJugadoresResponseDto compare(@RequestParam @Positive Long jugadorAId,
            @RequestParam @Positive Long jugadorBId,
            @RequestParam(required = false) @Positive @Max(32767) Integer temporada,
            @RequestParam(required = false) @Positive Long torneoId) {
        return service.compare(jugadorAId, jugadorBId, temporada, torneoId);
    }
}

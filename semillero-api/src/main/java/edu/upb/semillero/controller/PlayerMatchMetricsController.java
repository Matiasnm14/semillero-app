package edu.upb.semillero.controller;

import edu.upb.semillero.repository.dto.response.PlayerMatchMetricResponseDto;
import edu.upb.semillero.service.PlayerMatchMetricsQueryService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metricas")
public class PlayerMatchMetricsController {
    private final PlayerMatchMetricsQueryService service;

    public PlayerMatchMetricsController(PlayerMatchMetricsQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlayerMatchMetricResponseDto> find(
            @RequestParam(required = false) @Positive Long jugadorId,
            @RequestParam(required = false) @Positive Long partidoId,
            @RequestParam(required = false) @Positive Long clubId) {
        if (jugadorId != null && partidoId == null && clubId == null) return service.forPlayer(jugadorId);
        if (jugadorId == null && partidoId != null && clubId != null) {
            return service.forMatchAndClub(partidoId, clubId);
        }
        throw new IllegalArgumentException("Indica jugadorId o partidoId junto con clubId");
    }
}

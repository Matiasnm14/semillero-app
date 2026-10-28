package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.response.PlayerMatchMetricResponseDto;
import edu.upb.semillero.repository.entity.PlayerMatchMetrics;
import edu.upb.semillero.repository.repos.PlayerMatchMetricsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlayerMatchMetricsQueryService {
    private final PlayerMatchMetricsRepository repository;

    public PlayerMatchMetricsQueryService(PlayerMatchMetricsRepository repository) {
        this.repository = repository;
    }

    public List<PlayerMatchMetricResponseDto> forPlayer(Long jugadorId) {
        return repository.findByJugadorIdAndCurrentTrueOrderByPartidoFechaInicioDesc(jugadorId)
                .stream().map(this::toResponse).toList();
    }

    public List<PlayerMatchMetricResponseDto> forMatchAndClub(Long partidoId, Long clubId) {
        return repository.findCurrentByPartidoIdAndClubId(partidoId, clubId)
                .stream().map(this::toResponse).toList();
    }

    private PlayerMatchMetricResponseDto toResponse(PlayerMatchMetrics metric) {
        var jugador = metric.getJugador();
        var usuario = jugador.getUsuario();
        String nombre = String.join(" ",
                usuario.getNombres() == null ? "" : usuario.getNombres(),
                usuario.getApellidos() == null ? "" : usuario.getApellidos()).trim();
        var partido = metric.getPartido();
        return new PlayerMatchMetricResponseDto(
                metric.getId(), jugador.getId(), nombre, partido.getId(), partido.getFechaInicio(), partido.getEstado(),
                metric.getTotalDistance(), metric.getAvgSpeed(), metric.getMaxSpeed(),
                metric.getPeriodEnd().subtract(metric.getPeriodStart())
                        .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
    }
}

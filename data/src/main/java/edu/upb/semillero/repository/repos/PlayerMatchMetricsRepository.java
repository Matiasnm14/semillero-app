package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.PlayerMatchMetrics;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerMatchMetricsRepository extends JpaRepository<PlayerMatchMetrics, Long> {
    @EntityGraph(attributePaths = {"analysis", "partido", "jugador"})
    List<PlayerMatchMetrics> findByAnalysisIdOrderById(Long analysisId);

    Optional<PlayerMatchMetrics> findByAnalysisIdAndJugadorIdAndPeriodStartAndPeriodEnd(
            Long analysisId, Long jugadorId, BigDecimal periodStart, BigDecimal periodEnd);

    @EntityGraph(attributePaths = {"partido", "jugador", "jugador.usuario"})
    List<PlayerMatchMetrics> findByJugadorIdAndCurrentTrueOrderByPartidoFechaInicioDesc(Long jugadorId);

    @EntityGraph(attributePaths = {"partido", "jugador", "jugador.usuario"})
    @Query("""
            select metric from PlayerMatchMetrics metric
            join ParticipacionPartido participacion
              on participacion.partido = metric.partido and participacion.jugador = metric.jugador
            where metric.partido.id = :partidoId
              and participacion.club.id = :clubId
              and metric.current = true
            order by metric.jugador.id
            """)
    List<PlayerMatchMetrics> findCurrentByPartidoIdAndClubId(
            @Param("partidoId") Long partidoId, @Param("clubId") Long clubId);
}

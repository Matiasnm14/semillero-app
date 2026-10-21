package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.PlayerMatchMetrics;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerMatchMetricsRepository extends JpaRepository<PlayerMatchMetrics, Long> {
    @EntityGraph(attributePaths = {"analysis", "partido", "jugador"})
    List<PlayerMatchMetrics> findByAnalysisIdOrderById(Long analysisId);

    Optional<PlayerMatchMetrics> findByAnalysisIdAndJugadorIdAndPeriodStartAndPeriodEnd(
            Long analysisId, Long jugadorId, BigDecimal periodStart, BigDecimal periodEnd);
}

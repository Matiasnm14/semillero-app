package edu.upb.semillero;

import edu.upb.semillero.repository.entity.AnalysisJob;
import edu.upb.semillero.repository.enums.EstadoAnalisis;
import edu.upb.semillero.service.analysis.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class AnalysisSnapshotValidatorTest {
    private final AnalysisSnapshotValidator validator = new AnalysisSnapshotValidator();
    private final Instant now = Instant.parse("2026-10-21T19:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"wrong-id", "missing-revision", "negative-revision", "nan", "infinity", "negative-percentage",
            "overflow-percentage", "negative-frames", "zero-total", "overflow-frames", "missing-start", "missing-end",
            "early-end", "premature-results", "missing-result-ref", "missing-state", "missing-results-flag"})
    void rejectsInvalidProviderData(String scenario) {
        var job = job();
        Long id = scenario.equals("wrong-id") ? 2L : 1L;
        Long revision = scenario.equals("missing-revision") ? null : scenario.equals("negative-revision") ? -1L : 1L;
        Double percentage = switch (scenario) {
            case "nan" -> Double.NaN;
            case "infinity" -> Double.POSITIVE_INFINITY;
            case "negative-percentage" -> -1.0;
            case "overflow-percentage" -> 101.0;
            default -> null;
        };
        Long frames = scenario.equals("negative-frames") ? -1L : scenario.equals("overflow-frames") ? 101L : 0L;
        var state = scenario.equals("missing-state") ? null
                : scenario.equals("missing-end") || scenario.equals("early-end") || scenario.equals("missing-result-ref")
                ? EstadoAnalisis.COMPLETADO : EstadoAnalisis.PROCESANDO;
        Instant start = scenario.equals("missing-start") ? null : now;
        Instant end = scenario.equals("early-end") ? now.minusSeconds(1)
                : scenario.equals("missing-result-ref") ? now : null;
        Boolean available = scenario.equals("missing-results-flag") ? null
                : scenario.equals("premature-results") || scenario.equals("missing-result-ref");
        var snapshot = new AnalysisGateway.Snapshot(id, revision, state, percentage, frames,
                scenario.equals("zero-total") ? 0L : 100L, start, end, available, null, null);
        assertThatThrownBy(() -> validator.isNewer(snapshot, job)).isInstanceOf(AnalysisGatewayException.class);
    }

    @Test
    void preventsRegressingStateProgressAndInconsistentPartialFrameUpdates() {
        var job = job();
        job.setEstado(EstadoAnalisis.PROCESANDO);
        job.setFechaInicio(now);
        job.setPorcentaje(50.0);
        job.setFotogramasProcesados(50L);
        job.setFotogramasTotales(100L);
        var pending = new AnalysisGateway.Snapshot(1L, 1L, EstadoAnalisis.PENDIENTE, null, null, null, null, null, false, null, null);
        assertThatThrownBy(() -> validator.isNewer(pending, job)).isInstanceOf(AnalysisGatewayException.class);
        var backwards = new AnalysisGateway.Snapshot(1L, 1L, EstadoAnalisis.PROCESANDO, 40.0, 40L, 100L, now, null, false, null, null);
        assertThatThrownBy(() -> validator.isNewer(backwards, job)).isInstanceOf(AnalysisGatewayException.class);
        var partial = new AnalysisGateway.Snapshot(1L, 1L, EstadoAnalisis.PROCESANDO, null, 101L, null, now, null, false, null, null);
        assertThatThrownBy(() -> validator.isNewer(partial, job)).isInstanceOf(AnalysisGatewayException.class);
    }

    @Test
    void acceptsUnknownProgressAndCompletedResultReferencesButIgnoresOldRevisions() {
        var job = job();
        var snapshot = new AnalysisGateway.Snapshot(1L, 3L, EstadoAnalisis.COMPLETADO, null, null, null,
                now, now.plusSeconds(1), true, "resultados:1", null);
        assertThat(validator.isNewer(snapshot, job)).isTrue();
        job.setRevisionRemota(3L);
        assertThat(validator.isNewer(snapshot, job)).isFalse();
    }

    private AnalysisJob job() {
        var job = new AnalysisJob();
        job.setId(1L);
        job.setEstado(EstadoAnalisis.PENDIENTE);
        return job;
    }
}

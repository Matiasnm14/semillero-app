package edu.upb.semillero.service.analysis;

import edu.upb.semillero.repository.enums.EstadoAnalisis;
import java.time.Instant;

/** Contract for the Python service; the original is resolved in its read-only shared volume. */
public interface AnalysisGateway {
    record Submission(Long analysisId, Long videoId, Long partidoId, String storageId, String sha256) {}

    record Snapshot(Long analysisId, Long revision, EstadoAnalisis estado, Double porcentaje,
            Long fotogramasProcesados, Long fotogramasTotales, Instant fechaInicio, Instant fechaFin,
            Boolean resultadosDisponibles, String resultadoRef, String codigoError) {}

    Snapshot submit(Submission submission);

    Snapshot getStatus(Long analysisId);
}

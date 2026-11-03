package edu.upb.semillero.service.analysis;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import edu.upb.semillero.repository.enums.EstadoHeatmap;

/** Spark accepts a job; the Future completes after results and the optional PNG are received. */
public interface AnalysisGateway {
    record Progress(Double processedSeconds, Double totalSeconds, Double percentage) {}

    /** Callbacks return only after their durable transaction commits; failures stop the next download. */
    record Submission(String correlationId, Path original, boolean resume, Consumer<Progress> progress,
            String savedResults, EstadoHeatmap savedHeatmap, Consumer<String> saveResults,
            Consumer<EstadoHeatmap> saveHeatmap) {
        public Submission(String correlationId, Path original, boolean resume, Consumer<Progress> progress) {
            this(correlationId, original, resume, progress, null, EstadoHeatmap.PENDIENTE, value -> {}, value -> {});
        }
        public Submission(String correlationId, Path original) {
            this(correlationId, original, false, value -> {});
        }
        /** Existing jobs have a durable ID; uploads use a correlation before any SQL insert. */
        public Submission(Long analysisId, Path original) {
            this(analysisId.toString(), original);
        }
    }
    CompletableFuture<String> evaluate(Submission submission);

    /** Legacy cleanup only. Current Spark deletes automatically after both deliveries. */
    default CompletableFuture<Void> cleanup(String analysisId) {
        return CompletableFuture.completedFuture(null);
    }
}

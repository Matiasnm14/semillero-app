package edu.upb.semillero.service.analysis;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Spark accepts a job; the Future completes after results and the optional PNG are received. */
public interface AnalysisGateway {
    record Progress(Double processedSeconds, Double totalSeconds, Double percentage) {}

    record Submission(String correlationId, Path original, boolean resume, Consumer<Progress> progress) {
        public Submission(String correlationId, Path original) {
            this(correlationId, original, false, value -> {});
        }
        /** Existing jobs have a durable ID; uploads use a correlation before any SQL insert. */
        public Submission(Long analysisId, Path original) {
            this(analysisId.toString(), original);
        }
    }
    CompletableFuture<String> evaluate(Submission submission);

    /** Only call after the database transaction storing the JSON has committed. */
    default CompletableFuture<Void> cleanup(String analysisId) {
        return CompletableFuture.completedFuture(null);
    }
}

package edu.upb.semillero.service.analysis;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Python evaluates the original and completes one response; all domain context stays in Spring. */
public interface AnalysisGateway {
    record Submission(String correlationId, Path original) {
        /** Existing jobs have a durable ID; uploads use a correlation before any SQL insert. */
        public Submission(Long analysisId, Path original) {
            this(analysisId.toString(), original);
        }
    }
    CompletableFuture<String> evaluate(Submission submission);
}

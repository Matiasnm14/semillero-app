package edu.upb.semillero.service.analysis;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Python evaluates the original and completes one response; all domain context stays in Spring. */
public interface AnalysisGateway {
    record Submission(Long analysisId, Path original) {}
    CompletableFuture<String> evaluate(Submission submission);
}

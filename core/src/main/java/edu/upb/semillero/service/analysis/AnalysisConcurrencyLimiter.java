package edu.upb.semillero.service.analysis;

import edu.upb.semillero.config.AnalysisProperties;
import org.springframework.stereotype.Component;

/** Shares this instance's evaluation capacity between direct uploads and queued jobs. */
@Component
public class AnalysisConcurrencyLimiter {
    private final AnalysisProperties properties;
    private int active;

    public AnalysisConcurrencyLimiter(AnalysisProperties properties) {
        this.properties = properties;
    }

    /** Returns immediately: requests without capacity must not form an unbounded queue. */
    public synchronized Permit tryAcquire() {
        if (active >= properties.getMaxConcurrentJobs()) return null;
        active++;
        return new Permit();
    }

    public final class Permit implements AutoCloseable {
        private boolean released;

        private Permit() {}

        @Override
        public void close() {
            synchronized (AnalysisConcurrencyLimiter.this) {
                if (!released) {
                    released = true;
                    active--;
                }
            }
        }
    }
}

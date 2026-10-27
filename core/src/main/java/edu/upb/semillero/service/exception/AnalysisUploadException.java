package edu.upb.semillero.service.exception;

import edu.upb.semillero.service.analysis.AnalysisGatewayException;

/** A rejected upload never exposes the producer's response or transport exception. */
public class AnalysisUploadException extends RuntimeException {
    private final AnalysisGatewayException.Reason reason;

    public AnalysisUploadException(AnalysisGatewayException.Reason reason) {
        super(reason.message());
        this.reason = reason;
    }

    public boolean isTimeout() {
        return reason == AnalysisGatewayException.Reason.TIMEOUT || reason == AnalysisGatewayException.Reason.DEADLINE;
    }
}

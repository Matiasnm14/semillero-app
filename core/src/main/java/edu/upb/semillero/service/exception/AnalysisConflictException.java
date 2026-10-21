package edu.upb.semillero.service.exception;

public class AnalysisConflictException extends ConflictException {
    private final Long analysisId;

    public AnalysisConflictException(Long analysisId, boolean active) {
        super(active ? "El video ya tiene un análisis activo"
                : "El video ya fue analizado; el reproceso requiere un nuevo intento confirmado (HU16)");
        this.analysisId = analysisId;
    }

    public Long getAnalysisId() {
        return analysisId;
    }
}

package edu.upb.semillero.service.analysis;

public class AnalysisGatewayException extends RuntimeException {
    public enum Reason {
        TRANSPORT("ANALIZADOR_NO_DISPONIBLE", "No se pudo completar la comunicación con el servicio de análisis"),
        INVALID_RESPONSE("RESPUESTA_ANALIZADOR_INVALIDA", "El servicio de análisis devolvió una respuesta incompatible"),
        REJECTED("SOLICITUD_ANALIZADOR_RECHAZADA", "El servicio de análisis no pudo aceptar la evaluación"),
        MISSING_ORIGINAL("ORIGINAL_NO_DISPONIBLE", "El archivo original ya no está disponible para analizar"),
        DEADLINE("TIEMPO_ANALISIS_AGOTADO", "El análisis superó el plazo máximo desde su solicitud"),
        TIMEOUT("ESPERA_EVALUACION_AGOTADA", "La evaluación no devolvió resultados dentro del tiempo permitido"),
        INTERRUPTED("EVALUACION_INTERRUMPIDA", "Se perdió la espera del resultado; la evaluación requiere revisión antes de reprocesar");

        private final String code;
        private final String message;
        Reason(String code, String message) { this.code = code; this.message = message; }
        public String code() { return code; }
        public String message() { return message; }
    }

    private final Reason reason;
    public AnalysisGatewayException(Reason reason) { super(reason.message()); this.reason = reason; }
    public Reason reason() { return reason; }
}

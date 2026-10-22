package edu.upb.semillero.service.analysis;

public class AnalysisGatewayException extends RuntimeException {
    public enum Reason {
        TRANSPORT("ANALIZADOR_NO_DISPONIBLE", "No se pudo contactar al servicio de análisis", true),
        INVALID_RESPONSE("RESPUESTA_ANALIZADOR_INVALIDA", "El servicio de análisis devolvió una respuesta incompatible", false),
        REJECTED("SOLICITUD_ANALIZADOR_RECHAZADA", "El servicio de análisis no pudo aceptar o recuperar el trabajo", false),
        MISSING_ORIGINAL("ORIGINAL_NO_DISPONIBLE", "El archivo original ya no está disponible para analizar", false),
        DEADLINE("TIEMPO_ANALISIS_AGOTADO", "El análisis superó el plazo máximo de seguimiento", false),
        RETRIES("RECUPERACION_AGOTADA", "No se pudo recuperar la comunicación con el servicio de análisis", false),
        PROCESSING("ANALISIS_FALLIDO", "El servicio no pudo completar el análisis del video", false),
        INVALID_VIDEO("VIDEO_INVALIDO", "El servicio de análisis no pudo procesar el contenido del video", false);

        private final String code;
        private final String message;
        private final boolean retryable;

        Reason(String code, String message, boolean retryable) {
            this.code = code;
            this.message = message;
            this.retryable = retryable;
        }

        public String code() { return code; }
        public String message() { return message; }
        public boolean retryable() { return retryable; }
    }

    private final Reason reason;

    public AnalysisGatewayException(Reason reason) {
        super(reason.message());
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}

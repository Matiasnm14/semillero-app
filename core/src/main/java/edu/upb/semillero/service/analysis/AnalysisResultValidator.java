package edu.upb.semillero.service.analysis;

import edu.upb.semillero.config.AnalysisProperties;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import static edu.upb.semillero.service.analysis.AnalysisGatewayException.Reason.INVALID_RESPONSE;

/** Syntax/size validation, also compatible with stored historical JSON. Spark envelopes are checked by the gateway. */
@Component
public class AnalysisResultValidator {
    private final ObjectMapper json;
    private final AnalysisProperties properties;

    public AnalysisResultValidator(ObjectMapper json, AnalysisProperties properties) {
        this.json = json;
        this.properties = properties;
    }

    public String validate(String payload) {
        if (payload == null || payload.isBlank()
                || payload.getBytes(StandardCharsets.UTF_8).length > properties.getMaxResponseBytes()) throw invalid();
        try {
            var root = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(payload);
            if (root == null || !root.isObject() && !root.isArray()) throw invalid();
            return payload; // Preserve numeric precision and the original field names, without mapping players.
        } catch (RuntimeException e) { throw invalid(); }
    }

    private AnalysisGatewayException invalid() {
        return new AnalysisGatewayException(INVALID_RESPONSE);
    }
}

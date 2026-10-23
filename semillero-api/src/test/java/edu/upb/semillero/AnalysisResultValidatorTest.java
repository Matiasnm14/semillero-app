package edu.upb.semillero;

import edu.upb.semillero.config.AnalysisProperties;
import edu.upb.semillero.service.analysis.AnalysisGatewayException;
import edu.upb.semillero.service.analysis.AnalysisResultValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

class AnalysisResultValidatorTest {
    private final AnalysisProperties properties = new AnalysisProperties();
    private final AnalysisResultValidator validator = new AnalysisResultValidator(new ObjectMapper(), properties);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "null", "42", "true", "\"text\"", "{bad json}", "[] {}", "[] trailing"})
    void rejectsUnstructuredMalformedOrTrailingPayloads(String body) {
        assertThatThrownBy(() -> validator.validate(body)).isInstanceOf(AnalysisGatewayException.class);
    }

    @Test
    void preservesExactNumbersAndAcceptsUnknownFieldsWithoutInventingPlayerIds() {
        String body = "[{\"dorsal\":9,\"distancia\":12.1234567890123456789,\"campo_nuevo\":true}]";
        assertThat(validator.validate(body)).isEqualTo(body);
        assertThat(validator.validate("{}")).isEqualTo("{}");
        assertThat(validator.validate("[]")).isEqualTo("[]");
    }

    @Test
    void countsUtf8BytesRatherThanCharacters() {
        properties.setMaxResponseBytes(20);
        assertThatThrownBy(() -> validator.validate("{\"x\":\"áááááááá\"}"))
                .isInstanceOf(AnalysisGatewayException.class);
    }
}

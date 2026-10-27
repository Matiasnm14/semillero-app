package edu.upb.semillero.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "analysis")
public class AnalysisProperties {
    private boolean enabled;
    private boolean workerEnabled = true;
    private URI baseUrl;
    private String evaluationPath = "/internal/v1/evaluaciones";
    private String token = "";
    private Duration pollInterval = Duration.ofSeconds(5);
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofHours(2);
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration maxDuration = Duration.ofHours(6);
    @Min(1)
    private int maxConcurrentJobs = 1;
    @Min(2)
    private int maxResponseBytes = 1024 * 1024;

    @AssertTrue(message = "analysis requiere una URL HTTP(S) sin usuario, query ni fragmento al habilitarse")
    public boolean isEndpointValid() {
        return !enabled || baseUrl != null && baseUrl.getHost() != null
                && ("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme()))
                && baseUrl.getUserInfo() == null && baseUrl.getQuery() == null && baseUrl.getFragment() == null;
    }

    @AssertTrue(message = "analysis.evaluation-path debe ser una ruta absoluta sin query, fragmento ni segmentos relativos")
    public boolean isEvaluationPathValid() {
        if (evaluationPath == null || !evaluationPath.startsWith("/") || evaluationPath.startsWith("//")) return false;
        try {
            URI path = URI.create(evaluationPath);
            return path.getRawQuery() == null && path.getRawFragment() == null
                    && path.normalize().equals(path) && !evaluationPath.contains("..");
        } catch (IllegalArgumentException e) { return false; }
    }

    @AssertTrue(message = "los intervalos deben ser positivos, conexión <= espera y reserva > dos intervalos del worker")
    public boolean isTimingValid() {
        return positive(pollInterval) && positive(connectTimeout) && positive(requestTimeout) && positive(leaseDuration)
                && positive(maxDuration) && connectTimeout.compareTo(requestTimeout) <= 0
                && leaseDuration.compareTo(pollInterval.multipliedBy(2)) > 0;
    }

    private boolean positive(Duration value) {
        return value != null && value.compareTo(Duration.ofMillis(1)) >= 0;
    }
}

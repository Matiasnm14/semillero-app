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
    private String token = "";
    private Duration pollInterval = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(10);
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration maxDuration = Duration.ofHours(6);
    @Min(1)
    private int maxFailures = 5;

    @AssertTrue(message = "analysis requiere una URL HTTP(S) sin usuario, query ni fragmento al habilitarse")
    public boolean isEndpointValid() {
        return !enabled || baseUrl != null && baseUrl.getHost() != null
                && ("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme()))
                && baseUrl.getUserInfo() == null && baseUrl.getQuery() == null && baseUrl.getFragment() == null;
    }

    @AssertTrue(message = "los intervalos de analysis deben ser positivos y la reserva mayor que dos timeouts HTTP")
    public boolean isTimingValid() {
        return positive(pollInterval) && positive(requestTimeout) && positive(leaseDuration)
                && positive(maxDuration) && leaseDuration.compareTo(requestTimeout.multipliedBy(2)) > 0;
    }

    private boolean positive(Duration value) {
        return value != null && value.compareTo(Duration.ofMillis(1)) >= 0;
    }
}

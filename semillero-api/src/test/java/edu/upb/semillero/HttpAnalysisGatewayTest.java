package edu.upb.semillero;

import com.sun.net.httpserver.HttpServer;
import edu.upb.semillero.config.AnalysisProperties;
import edu.upb.semillero.repository.enums.EstadoAnalisis;
import edu.upb.semillero.service.analysis.AnalysisGateway;
import edu.upb.semillero.service.analysis.AnalysisGatewayException;
import edu.upb.semillero.service.analysis.HttpAnalysisGateway;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static edu.upb.semillero.service.analysis.AnalysisGatewayException.Reason.*;
import static org.assertj.core.api.Assertions.*;

class HttpAnalysisGatewayTest {
    private HttpServer server;
    private HttpAnalysisGateway gateway;
    private AnalysisProperties properties;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> request = new AtomicReference<>();
    private int status = 200;
    private byte[] body;
    private CountDownLatch holdBody;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/analisis/101", exchange -> {
            method.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Location", "/must-not-follow");
                exchange.sendResponseHeaders(status, body.length);
                if (holdBody != null) holdBody.await(5, TimeUnit.SECONDS);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        properties = new AnalysisProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
        properties.setToken("test-internal-token");
        gateway = new HttpAnalysisGateway(properties, json);
        body = json.writeValueAsBytes(new AnalysisGateway.Snapshot(101L, 1L, EstadoAnalisis.PROCESANDO,
                null, 100L, null, Instant.parse("2026-10-21T19:00:00Z"), null, false, null, null));
    }

    @AfterEach
    void cleanup() {
        if (holdBody != null) holdBody.countDown();
        if (gateway != null) gateway.close();
        if (server != null) server.stop(0);
    }

    @Test
    void sendsIdempotentPutWithSharedStorageIdentityAndReadsRealProgress() {
        status = 202;
        var submission = new AnalysisGateway.Submission(101L, 7L, 1L,
                "3b4e6f22-4550-4da6-9c99-63085e5d2a3c", "a".repeat(64));
        var result = gateway.submit(submission);
        assertThat(result.estado()).isEqualTo(EstadoAnalisis.PROCESANDO);
        assertThat(result.fotogramasProcesados()).isEqualTo(100);
        assertThat(result.porcentaje()).isNull();
        assertThat(method.get()).isEqualTo("PUT");
        assertThat(authorization.get()).isEqualTo("Bearer test-internal-token");
        var sent = json.readTree(request.get());
        assertThat(sent.path("analysisId").asLong()).isEqualTo(101);
        assertThat(sent.path("storageId").asText()).isEqualTo(submission.storageId());
        assertThat(sent.has("path")).isFalse();
        gateway.submit(submission);
        assertThat(json.readTree(request.get())).isEqualTo(sent);
    }

    @Test
    void pollsWithGetWithoutResubmittingVideo() {
        gateway.getStatus(101L);
        assertThat(method.get()).isEqualTo("GET");
        assertThat(request.get()).isEmpty();
    }

    @Test
    void retriesServiceOutageButSanitizesPermanentRejection() {
        body = "secret remote stack trace with token".getBytes(StandardCharsets.UTF_8);
        status = 503;
        assertReason(TRANSPORT);
        status = 401;
        assertReason(REJECTED);
    }

    @Test
    void doesNotFollowRedirectOrAcceptMalformedOrOversizedResponses() {
        status = 302;
        assertReason(REJECTED);
        status = 200;
        body = "{not-json secret}".getBytes(StandardCharsets.UTF_8);
        assertReason(INVALID_RESPONSE);
        body = new byte[65 * 1024];
        assertReason(INVALID_RESPONSE);
    }

    @Test
    void timesOutEvenWhenHeadersArriveButResponseBodyStalls() {
        holdBody = new CountDownLatch(1);
        properties.setRequestTimeout(Duration.ofMillis(200));
        assertReason(TRANSPORT);
    }

    private void assertReason(AnalysisGatewayException.Reason reason) {
        assertThatThrownBy(() -> gateway.getStatus(101L)).isInstanceOfSatisfying(AnalysisGatewayException.class, error -> {
            assertThat(error.reason()).isEqualTo(reason);
            assertThat(error.getMessage()).doesNotContain("secret", "test-internal-token", "127.0.0.1");
        });
    }
}

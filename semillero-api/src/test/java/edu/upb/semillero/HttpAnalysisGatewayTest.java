package edu.upb.semillero;

import com.sun.net.httpserver.HttpServer;
import edu.upb.semillero.config.AnalysisProperties;
import edu.upb.semillero.service.analysis.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static edu.upb.semillero.service.analysis.AnalysisGatewayException.Reason.*;
import static org.assertj.core.api.Assertions.*;

class HttpAnalysisGatewayTest {
    @TempDir Path root;
    private HttpServer server;
    private HttpAnalysisGateway gateway;
    private AnalysisProperties properties;
    private ExecutorService executor;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> correlation = new AtomicReference<>();
    private final AtomicReference<byte[]> request = new AtomicReference<>();
    private int status = 200;
    private byte[] body = "[{\"dorsal\":9}]".getBytes(StandardCharsets.UTF_8);
    private CountDownLatch holdBody;
    private final CountDownLatch received = new CountDownLatch(1);
    private Path original;

    @BeforeEach
    void setup() throws Exception {
        original = root.resolve("original.mp4");
        Files.writeString(original, "video-test-bytes");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/prefix/evaluate", exchange -> {
            method.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            correlation.set(exchange.getRequestHeaders().getFirst("X-Analysis-Id"));
            request.set(exchange.getRequestBody().readAllBytes());
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Location", "/must-not-follow");
                exchange.sendResponseHeaders(status, body.length);
                received.countDown();
                if (holdBody != null) holdBody.await(5, TimeUnit.SECONDS);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        properties = new AnalysisProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/prefix/"));
        properties.setEvaluationPath("/evaluate");
        properties.setToken("test-internal-token");
        gateway = new HttpAnalysisGateway(properties, new AnalysisResultValidator(new ObjectMapper(), properties));
    }

    @AfterEach
    void cleanup() {
        if (holdBody != null) holdBody.countDown();
        if (gateway != null) gateway.close();
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void streamsMultipartVideoAndCorrelatesLocallyWithoutSendingMatchMetadata() throws Exception {
        assertThat(evaluate().get(5, TimeUnit.SECONDS)).isEqualTo(new String(body, StandardCharsets.UTF_8));
        assertThat(method.get()).isEqualTo("POST");
        assertThat(authorization.get()).isEqualTo("Bearer test-internal-token");
        assertThat(correlation.get()).isEqualTo("101");
        assertThat(contentType.get()).startsWith("multipart/form-data; boundary=Semillero");
        String sent = new String(request.get(), StandardCharsets.UTF_8);
        assertThat(sent).contains("name=\"file\"; filename=\"video.mp4\"", "video-test-bytes")
                .doesNotContain("partidoId", "storageId", "torneo", root.toString());
    }

    @Test
    void returnsPendingFutureUntilTheCompleteBodyArrives() throws Exception {
        holdBody = new CountDownLatch(1);
        var result = evaluate();
        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(result.isDone()).isFalse();
        holdBody.countDown();
        assertThat(result.get(5, TimeUnit.SECONDS)).contains("dorsal");
    }

    @Test
    void sanitizesErrorsWithoutAutomaticRetransmission() {
        body = "secret remote stack trace with token".getBytes(StandardCharsets.UTF_8);
        status = 503;
        assertReason(TRANSPORT);
        status = 401;
        assertReason(REJECTED);
    }

    @Test
    void rejectsAcknowledgementsRedirectsMalformedAndOversizedResults() {
        status = 202; assertReason(REJECTED);
        status = 302; assertReason(REJECTED);
        status = 200;
        body = "{not-json secret}".getBytes(StandardCharsets.UTF_8); assertReason(INVALID_RESPONSE);
        body = new byte[1024 * 1024 + 1]; assertReason(INVALID_RESPONSE);
    }

    @Test
    void timesOutWhenHeadersArriveButBodyStallsAndCancelsTheExchange() throws Exception {
        holdBody = new CountDownLatch(1);
        properties.setConnectTimeout(Duration.ofMillis(100));
        properties.setRequestTimeout(Duration.ofMillis(300));
        var result = evaluate();
        assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                .satisfies(error -> assertThat(error.getCause() instanceof TimeoutException
                        || error.getCause() instanceof AnalysisGatewayException).isTrue());
    }

    @Test
    void callerCanCancelPendingEvaluation() throws Exception {
        holdBody = new CountDownLatch(1);
        var result = evaluate();
        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(result.cancel(true)).isTrue();
        assertThat(result.isCancelled()).isTrue();
    }

    @Test
    void missingOriginalFailsBeforeMakingARequest() throws Exception {
        Files.delete(original);
        assertReason(MISSING_ORIGINAL);
        assertThat(method.get()).isNull();
    }

    private CompletableFuture<String> evaluate() {
        return gateway.evaluate(new AnalysisGateway.Submission(101L, original));
    }

    private void assertReason(AnalysisGatewayException.Reason reason) {
        assertThatThrownBy(() -> evaluate().get(5, TimeUnit.SECONDS)).isInstanceOfSatisfying(ExecutionException.class, error -> {
            assertThat(error.getCause()).isInstanceOfSatisfying(AnalysisGatewayException.class, failure -> {
                assertThat(failure.reason()).isEqualTo(reason);
                assertThat(failure.getMessage()).doesNotContain("secret", "test-internal-token", "127.0.0.1");
            });
        });
    }
}

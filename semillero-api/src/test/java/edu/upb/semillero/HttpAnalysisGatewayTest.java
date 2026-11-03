package edu.upb.semillero;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import edu.upb.semillero.config.AnalysisProperties;
import edu.upb.semillero.repository.enums.EstadoHeatmap;
import edu.upb.semillero.service.analysis.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static edu.upb.semillero.service.analysis.AnalysisGatewayException.Reason.*;
import static org.assertj.core.api.Assertions.*;

class HttpAnalysisGatewayTest {
    @TempDir Path root;
    private HttpServer server;
    private HttpAnalysisGateway gateway;
    private AnalysisProperties properties;
    private AnalysisArtifactStorage artifacts;
    private ExecutorService executor;
    private Path original;
    private final AtomicInteger uploads = new AtomicInteger(), polls = new AtomicInteger(), deletes = new AtomicInteger();
    private final AtomicInteger resultGets = new AtomicInteger(), heatmapGets = new AtomicInteger();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> multipart = new AtomicReference<>();
    private final List<AnalysisGateway.Progress> progress = new CopyOnWriteArrayList<>();
    private final CountDownLatch received = new CountDownLatch(1);
    private volatile String state = "COMPLETED";
    private volatile String id = "a8a0535c-30ed-4ac7-a862-0a486c234d10";
    private volatile int postStatus = 202, heatmapStatus = 200;
    private volatile String postBody;
    private volatile String statusBody;
    private volatile String results = "{\"analysisId\":\"a8a0535c-30ed-4ac7-a862-0a486c234d10\",\"status\":\"COMPLETED\",\"summary\":{},\"players\":[{\"jerseyNumber\":7,\"jerseyStatus\":\"consensus_unverified\",\"movement\":{\"distanceMeters\":null}}]}";
    private volatile byte[] png;
    private volatile CountDownLatch holdBody;
    private volatile boolean busyOnce;
    private volatile int heatmapDelayMillis;
    private volatile boolean statusMissingOnce;

    @BeforeEach
    void setup() throws Exception {
        original = root.resolve("original.mp4");
        Files.writeString(original, "video-test-bytes");
        png = png();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/prefix/analyses", this::handle);
        server.start();
        properties = new AnalysisProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/prefix/"));
        properties.setToken("test-internal-token");
        properties.setPollInterval(Duration.ofMillis(20));
        properties.setArtifactsDir(root.resolve("artifacts"));
        properties.setMaxDuration(Duration.ofSeconds(5));
        artifacts = new AnalysisArtifactStorage(properties);
        gateway = new HttpAnalysisGateway(properties, new AnalysisResultValidator(new ObjectMapper(), properties), artifacts, new ObjectMapper());
    }

    private void handle(HttpExchange exchange) throws java.io.IOException {
        try {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            String path = exchange.getRequestURI().getPath();
            if (exchange.getRequestMethod().equals("POST")) {
                int attempt = uploads.incrementAndGet();
                multipart.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                if (busyOnce && attempt == 1) { respond(exchange, 409, error("GPU_BUSY")); return; }
                String body = postBody != null ? postBody : "{\"analysisId\":\"" + id + "\",\"status\":\"QUEUED\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Location", "/must-not-follow");
                exchange.sendResponseHeaders(postStatus, body.getBytes(StandardCharsets.UTF_8).length);
                received.countDown();
                if (holdBody != null) holdBody.await(5, TimeUnit.SECONDS);
                exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
            } else if (exchange.getRequestMethod().equals("DELETE")) {
                deletes.incrementAndGet();
                exchange.sendResponseHeaders(204, -1);
            } else if (path.endsWith("/results")) {
                resultGets.incrementAndGet();
                respond(exchange, 200, results);
            } else if (path.endsWith("/heatmap")) {
                heatmapGets.incrementAndGet();
                if (heatmapDelayMillis > 0) Thread.sleep(heatmapDelayMillis);
                if (heatmapStatus == 422) { respond(exchange, 422, error("HEATMAP_UNAVAILABLE")); return; }
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                exchange.sendResponseHeaders(heatmapStatus, png.length);
                exchange.getResponseBody().write(png);
            } else {
                int attempt = polls.incrementAndGet();
                if (statusMissingOnce && attempt == 1) { respond(exchange, 404, error("ANALYSIS_NOT_FOUND")); return; }
                respond(exchange, 200, statusBody != null ? statusBody : "{\"analysisId\":\"" + id + "\",\"status\":\"" + state
                        + "\",\"progress\":{\"processedSeconds\":420.0,\"totalSeconds\":1851.0,\"percentage\":22.69},\"error\":null}");
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finally { exchange.close(); }
    }

    private void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private String error(String code) { return "{\"analysisId\":\"a8a0535c-30ed-4ac7-a862-0a486c234d10\",\"error\":{\"code\":\"" + code + "\",\"message\":\"secret remote details\"}}"; }

    @AfterEach
    void cleanup() {
        if (holdBody != null) holdBody.countDown();
        if (gateway != null) gateway.close();
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void uploadsContractMultipartThenReceivesJsonAndPngWithoutDeletingRemoteFiles() throws Exception {
        assertThat(evaluate().get(5, TimeUnit.SECONDS)).isEqualTo(results);
        assertThat(multipart.get()).contains("name=\"video\"; filename=\"video.mp4\"", "video-test-bytes",
                "name=\"analysisId\"\r\n\r\na8a0535c-30ed-4ac7-a862-0a486c234d10", "name=\"readJerseys\"\r\n\r\ntrue")
                .doesNotContain("partidoId", "storageId", "torneo", root.toString(), "name=\"file\"");
        assertThat(authorizations).hasSize(4).containsOnly("Bearer test-internal-token");
        assertThat(Files.readAllBytes(artifacts.heatmap("a8a0535c-30ed-4ac7-a862-0a486c234d10"))).isEqualTo(png);
        assertThat(deletes.get()).isZero();
        gateway.cleanup("a8a0535c-30ed-4ac7-a862-0a486c234d10").get(5, TimeUnit.SECONDS);
        assertThat(deletes.get()).isEqualTo(1);
        assertThat(artifacts.exists("a8a0535c-30ed-4ac7-a862-0a486c234d10")).isTrue();
    }

    @Test
    void queuedAndProcessingKeepFuturePendingAndReportRealProgress() throws Exception {
        state = "QUEUED";
        var result = evaluate();
        await(() -> !progress.isEmpty());
        assertThat(result.isDone()).isFalse();
        assertThat(progress.getFirst()).isEqualTo(new AnalysisGateway.Progress(420.0, 1851.0, 22.69));
        state = "PROCESSING";
        await(() -> polls.get() >= 2);
        assertThat(result.isDone()).isFalse();
        state = "COMPLETED";
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(results);
        assertThat(uploads.get()).isEqualTo(1);
    }

    @Test
    void usesCompletedProgressOnlyWhenSparkActuallyReportsIt() throws Exception {
        statusBody = "{\"analysisId\":\"a8a0535c-30ed-4ac7-a862-0a486c234d10\",\"status\":\"COMPLETED\",\"progress\":{\"processedSeconds\":1851.0,\"totalSeconds\":1851.0,\"percentage\":100}}";
        evaluate().get(5, TimeUnit.SECONDS);
        assertThat(progress).containsExactly(new AnalysisGateway.Progress(1851.0, 1851.0, 100.0));
        progress.clear();
        statusBody = "{\"analysisId\":\"a8a0535c-30ed-4ac7-a862-0a486c234d10\",\"status\":\"COMPLETED\",\"progress\":null}";
        evaluate().get(5, TimeUnit.SECONDS);
        assertThat(progress).isEmpty();
    }

    @Test
    void retriesUploadOnlyAfterExplicitGpuBusy() throws Exception {
        busyOnce = true;
        assertThat(evaluate().get(5, TimeUnit.SECONDS)).isEqualTo(results);
        assertThat(uploads.get()).isEqualTo(2);
    }

    @Test
    void existingIdentityRecoversStatusWithoutCreatingAnotherJob() throws Exception {
        postStatus = 409; postBody = error("ANALYSIS_ID_EXISTS");
        assertThat(evaluate().get(5, TimeUnit.SECONDS)).isEqualTo(results);
        assertThat(uploads.get()).isEqualTo(1);
    }

    @Test
    void resumeSkipsUploadAndDoesNotNeedTheOriginal() throws Exception {
        Files.delete(original);
        assertThat(gateway.evaluate(new AnalysisGateway.Submission("a8a0535c-30ed-4ac7-a862-0a486c234d10", original, true, progress::add))
                .get(5, TimeUnit.SECONDS)).isEqualTo(results);
        assertThat(uploads.get()).isZero();
    }

    @Test
    void unavailableCalibrationStillPreservesMetricsAndNullMeasurements() throws Exception {
        heatmapStatus = 422;
        assertThat(evaluate().get(5, TimeUnit.SECONDS)).contains("\"distanceMeters\":null", "consensus_unverified");
        assertThat(artifacts.exists("a8a0535c-30ed-4ac7-a862-0a486c234d10")).isFalse();
        assertThat(deletes.get()).isZero();
    }

    @Test
    void failedSparkJobDoesNotExposeInternalErrors() {
        state = "FAILED";
        assertReason(PROCESSING);
        assertThat(deletes.get()).isZero();
    }

    @Test
    void sanitizesErrorsAndNeverRetransmitsAfterServerOrAuthenticationFailure() {
        postStatus = 503; postBody = error("PROCESSING_ERROR");
        assertReason(TRANSPORT);
        assertThat(uploads.get()).isEqualTo(1);
        postStatus = 401;
        assertReason(REJECTED);
        assertThat(uploads.get()).isEqualTo(2);
    }

    @Test
    void rejectsRedirectsMalformedAcknowledgementsAndMismatchingIdentities() {
        postStatus = 302; assertReason(REJECTED);
        postStatus = 202; postBody = "{not-json secret}"; assertReason(INVALID_RESPONSE);
        postBody = null; id = "other-analysis"; assertReason(INVALID_RESPONSE);
    }

    @Test
    void rejectsResultsFromAnotherAnalysisAndNumericHeatmaps() {
        results = results.replace("\"a8a0535c-30ed-4ac7-a862-0a486c234d10\"", "\"other\""); assertReason(INVALID_RESPONSE);
        results = "{\"analysisId\":\"a8a0535c-30ed-4ac7-a862-0a486c234d10\",\"status\":\"COMPLETED\",\"summary\":{},\"players\":[],\"heatmap\":{}}";
        assertReason(INVALID_RESPONSE);
    }

    @Test
    void rejectsInvalidProgressAndOversizedResults() {
        state = "PROCESSING";
        statusBody = "{\"analysisId\":\"a8a0535c-30ed-4ac7-a862-0a486c234d10\",\"status\":\"PROCESSING\",\"progress\":{\"percentage\":a8a0535c-30ed-4ac7-a862-0a486c234d10}}";
        assertReason(INVALID_RESPONSE);
        statusBody = null; state = "COMPLETED";
        results = "x".repeat(properties.getMaxResponseBytes() + 1);
        assertReason(INVALID_RESPONSE);
    }

    @Test
    void invalidOrOversizedPngPreventsConfirmationAndRemoteDeletion() {
        png = "not a png".getBytes(StandardCharsets.UTF_8); assertReason(INVALID_RESPONSE);
        png = new byte[properties.getMaxHeatmapBytes() + 1]; assertReason(INVALID_RESPONSE);
        assertThat(deletes.get()).isZero();
    }

    @Test
    void aLostUploadAcknowledgementRecoversByGetAndDoesNotTreatAnEarly404AsPermissionToResubmit() throws Exception {
        holdBody = new CountDownLatch(1);
        statusMissingOnce = true;
        properties.setConnectTimeout(Duration.ofMillis(100));
        properties.setRequestTimeout(Duration.ofMillis(200));
        assertThat(evaluate().get(5, TimeUnit.SECONDS)).isEqualTo(results);
        assertThat(uploads.get()).isEqualTo(1);
        assertThat(polls.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void cancellationStopsPollingWithoutDeletingRemoteWork() throws Exception {
        state = "PROCESSING";
        var result = evaluate();
        await(() -> polls.get() > 0);
        assertThat(result.cancel(true)).isTrue();
        assertThat(result.isCancelled()).isTrue();
        assertThat(deletes.get()).isZero();
    }

    @Test
    void missingOriginalFailsBeforeMakingARequest() throws Exception {
        Files.delete(original);
        assertReason(MISSING_ORIGINAL);
        assertThat(uploads.get()).isZero();
    }

    @Test
    void waitsForDurableJsonBeforeHeatmapAndRetriesOnlyTheCheckpoint() throws Exception {
        var attempts = new AtomicInteger();
        var outcome = new AtomicReference<EstadoHeatmap>();
        var submission = new AnalysisGateway.Submission(id, original, false, progress::add,
                null, EstadoHeatmap.PENDIENTE, payload -> {
                    assertThat(heatmapGets.get()).isZero();
                    assertThat(payload).isEqualTo(results);
                    if (attempts.incrementAndGet() == 1) throw new AnalysisGatewayException(CHECKPOINT);
                }, outcome::set);
        assertThat(gateway.evaluate(submission).get(5, TimeUnit.SECONDS)).isEqualTo(results);
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(resultGets.get()).isEqualTo(1);
        assertThat(heatmapGets.get()).isEqualTo(1);
        assertThat(outcome.get()).isEqualTo(EstadoHeatmap.DISPONIBLE);
        assertThat(deletes.get()).isZero();
    }

    @Test
    void aLostClaimNeverTriggersTheSecondRemoteDelivery() throws Exception {
        var submission = new AnalysisGateway.Submission(id, original, false, progress::add,
                null, EstadoHeatmap.PENDIENTE, payload -> { throw new AnalysisGatewayException(INTERRUPTED); }, value -> {});
        assertThatThrownBy(() -> gateway.evaluate(submission).get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(AnalysisGatewayException.class);
        assertThat(heatmapGets.get()).isZero();
        assertThat(deletes.get()).isZero();
    }

    @Test
    void resumesTheMapFromSavedJsonWithoutStatusOrResultsRequests() throws Exception {
        var outcome = new AtomicReference<EstadoHeatmap>();
        var submission = new AnalysisGateway.Submission(id, original, true, progress::add,
                results, EstadoHeatmap.PENDIENTE, payload -> fail("No repetir JSON"), outcome::set);
        assertThat(gateway.evaluate(submission).get(5, TimeUnit.SECONDS)).isEqualTo(results);
        assertThat(uploads.get()).isZero();
        assertThat(polls.get()).isZero();
        assertThat(resultGets.get()).isZero();
        assertThat(heatmapGets.get()).isEqualTo(1);
        assertThat(outcome.get()).isEqualTo(EstadoHeatmap.DISPONIBLE);
    }

    @Test
    void resumesSavedPngAndUnavailableMapWithoutCallingADeletedRemoteJob() throws Exception {
        artifacts.store(id, png);
        var outcome = new AtomicReference<EstadoHeatmap>();
        var saved = new AnalysisGateway.Submission(id, original, true, progress::add,
                results, EstadoHeatmap.PENDIENTE, value -> {}, outcome::set);
        assertThat(gateway.evaluate(saved).get(5, TimeUnit.SECONDS)).isEqualTo(results);
        assertThat(outcome.get()).isEqualTo(EstadoHeatmap.DISPONIBLE);
        artifacts.discard(id);
        saved = new AnalysisGateway.Submission(id, original, true, progress::add,
                results, EstadoHeatmap.NO_DISPONIBLE, value -> {}, value -> fail("Ya confirmado"));
        assertThat(gateway.evaluate(saved).get(5, TimeUnit.SECONDS)).isEqualTo(results);
        assertThat(authorizations).isEmpty();
    }

    @Test
    void unavailableMapHasADurableOutcomeAndAnUncertainDownloadIsNotRepeated() throws Exception {
        heatmapStatus = 422;
        var outcome = new AtomicReference<EstadoHeatmap>();
        var submission = new AnalysisGateway.Submission(id, original, false, progress::add,
                null, EstadoHeatmap.PENDIENTE, value -> {}, outcome::set);
        gateway.evaluate(submission).get(5, TimeUnit.SECONDS);
        assertThat(outcome.get()).isEqualTo(EstadoHeatmap.NO_DISPONIBLE);
        heatmapStatus = 200;
        heatmapDelayMillis = 500;
        properties.setRequestTimeout(Duration.ofMillis(100));
        assertReason(TIMEOUT);
        assertThat(heatmapGets.get()).isEqualTo(2);
    }

    @Test
    void rejectsNonCanonicalUuidBeforeUploading() {
        for (String invalid : List.of("101", "1-1-1-1-1", id.toUpperCase(), "../job")) {
            assertThatThrownBy(() -> gateway.evaluate(new AnalysisGateway.Submission(invalid, original)).get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(AnalysisGatewayException.class);
        }
        assertThat(authorizations).isEmpty();
    }

    private CompletableFuture<String> evaluate() {
        return gateway.evaluate(new AnalysisGateway.Submission("a8a0535c-30ed-4ac7-a862-0a486c234d10", original, false, progress::add));
    }

    private void assertReason(AnalysisGatewayException.Reason reason) {
        assertThatThrownBy(() -> evaluate().get(5, TimeUnit.SECONDS)).isInstanceOfSatisfying(ExecutionException.class, error -> {
            assertThat(error.getCause()).isInstanceOfSatisfying(AnalysisGatewayException.class, failure -> {
                assertThat(failure.reason()).isEqualTo(reason);
                assertThat(failure.getMessage()).doesNotContain("secret", "test-internal-token", "127.0.0.1");
            });
        });
    }

    static byte[] png() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    private void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).isTrue();
    }
}

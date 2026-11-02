package edu.upb.semillero.service.analysis;

import edu.upb.semillero.config.AnalysisProperties;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static edu.upb.semillero.service.analysis.AnalysisGatewayException.Reason.*;

/** HTTP contract documented in docs/spark-implementation.md. Never follows remote artifact URLs. */
@Component
public class HttpAnalysisGateway implements AnalysisGateway {
    private final AnalysisProperties properties;
    private final AnalysisResultValidator validator;
    private final AnalysisArtifactStorage artifacts;
    private final ObjectMapper json;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile HttpClient client;

    public HttpAnalysisGateway(AnalysisProperties properties, AnalysisResultValidator validator,
            AnalysisArtifactStorage artifacts, ObjectMapper json) {
        this.properties = properties;
        this.validator = validator;
        this.artifacts = artifacts;
        this.json = json;
    }

    @Override
    public CompletableFuture<String> evaluate(Submission submission) {
        return execute(() -> evaluateBlocking(submission));
    }

    private String evaluateBlocking(Submission submission) throws Exception {
        requireConfigured();
        if (submission.correlationId() == null || !submission.correlationId().matches("[A-Za-z0-9_-]{1,64}")) throw failure(REJECTED);
        long deadline = System.nanoTime() + properties.getMaxDuration().toNanos();
        String path = "/" + submission.correlationId();
        if (!submission.resume()) submit(submission, deadline);
        while (true) {
            checkDeadline(deadline);
            var status = readJson(send(request(path).GET().build(), properties.getMaxResponseBytes()), 200);
            validateIdentity(status, submission.correlationId());
            String state = status.path("status").asString("");
            switch (state) {
                case "FAILED" -> throw failure(PROCESSING);
                case "COMPLETED" -> {
                    if (!status.path("progress").isNull() && !status.path("progress").isMissingNode()) {
                        submission.progress().accept(progress(status.path("progress")));
                    }
                    var response = send(request(path + "/results").GET().build(), properties.getMaxResponseBytes());
                    if (isError(response, 409, "ANALYSIS_NOT_READY")) { pause(deadline); continue; }
                    String payload = decodeJson(response, 200);
                    JsonNode result = json.readTree(payload);
                    validateIdentity(result, submission.correlationId());
                    if (!"COMPLETED".equals(result.path("status").asString(""))
                            || !result.path("summary").isObject() || !result.path("players").isArray()
                            || result.has("heatmap") || result.has("artifacts")) throw failure(INVALID_RESPONSE);
                    receiveHeatmap(path, submission.correlationId());
                    return payload;
                }
                case "QUEUED", "PROCESSING" -> {
                    submission.progress().accept(progress(status.path("progress")));
                    pause(deadline);
                }
                default -> throw failure(INVALID_RESPONSE);
            }
        }
    }

    private void submit(Submission submission, long deadline) throws Exception {
        while (true) {
            checkDeadline(deadline);
            String boundary = "Semillero" + UUID.randomUUID().toString().replace("-", "");
            String prefix = "--" + boundary
                    + "\r\nContent-Disposition: form-data; name=\"analysisId\"\r\n\r\n" + submission.correlationId()
                    + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"readJerseys\"\r\n\r\n"
                    + properties.isReadJerseys() + "\r\n--" + boundary
                    + "\r\nContent-Disposition: form-data; name=\"video\"; filename=\"video.mp4\"\r\n"
                    + "Content-Type: video/mp4\r\n\r\n";
            HttpRequest.BodyPublisher body;
            try {
                body = HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofString(prefix),
                        HttpRequest.BodyPublishers.ofFile(submission.original()),
                        HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n"));
            } catch (IOException e) { throw failure(MISSING_ORIGINAL); }
            var response = send(request("").header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(body).build(), properties.getMaxResponseBytes());
            // Only an explicit busy rejection permits uploading the file again. A lost POST is never repeated.
            if (isError(response, 409, "GPU_BUSY")) { pause(deadline); continue; }
            if (isError(response, 409, "ANALYSIS_ID_EXISTS")) return;
            var ack = readJson(response, 202);
            validateIdentity(ack, submission.correlationId());
            if (!"QUEUED".equals(ack.path("status").asString(""))) throw failure(INVALID_RESPONSE);
            return;
        }
    }

    private void receiveHeatmap(String path, String id) throws Exception {
        var response = send(request(path + "/heatmap").setHeader("Accept", "image/png, application/json")
                .GET().build(), properties.getMaxHeatmapBytes());
        if (isError(response, 422, "HEATMAP_UNAVAILABLE")) return;
        requireStatus(response, 200);
        if (!mediaType(response).equalsIgnoreCase("image/png")) throw failure(INVALID_RESPONSE);
        artifacts.store(id, response.body());
    }

    @Override
    public CompletableFuture<Void> cleanup(String analysisId) {
        return execute(() -> {
            requireConfigured();
            if (analysisId == null || !analysisId.matches("[A-Za-z0-9_-]{1,64}")) throw failure(REJECTED);
            var response = send(request("/" + analysisId).DELETE().build(), properties.getMaxResponseBytes());
            if (response.statusCode() != 204 && !isError(response, 404, "ANALYSIS_NOT_FOUND")) {
                requireStatus(response, 204);
            }
            return null;
        });
    }

    private Progress progress(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return new Progress(null, null, null);
        if (!node.isObject()) throw failure(INVALID_RESPONSE);
        Double processed = number(node, "processedSeconds"), total = number(node, "totalSeconds"), percent = number(node, "percentage");
        if (processed != null && processed < 0 || total != null && total <= 0
                || processed != null && total != null && processed > total
                || percent != null && (percent < 0 || percent > 100)) throw failure(INVALID_RESPONSE);
        return new Progress(processed, total, percent);
    }

    private Double number(JsonNode node, String name) {
        var value = node.path(name);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isNumber() || !Double.isFinite(value.asDouble())) throw failure(INVALID_RESPONSE);
        return value.asDouble();
    }

    private void validateIdentity(JsonNode value, String id) {
        if (!value.isObject() || !value.path("analysisId").isString() || !id.equals(value.path("analysisId").asString())) {
            throw failure(INVALID_RESPONSE);
        }
    }

    private void requireConfigured() {
        if (!properties.isEnabled() || !properties.isEndpointValid() || !properties.isEvaluationPathValid()) throw failure(REJECTED);
    }

    private HttpRequest.Builder request(String suffix) {
        String base = properties.getBaseUrl().toString().replaceAll("/+$", "");
        String path = properties.getEvaluationPath().replaceAll("/+$", "");
        var builder = HttpRequest.newBuilder(URI.create(base + path + suffix))
                .timeout(properties.getRequestTimeout()).header("Accept", "application/json");
        if (properties.getToken() != null && !properties.getToken().isBlank()) builder.header("Authorization", "Bearer " + properties.getToken());
        return builder;
    }

    private HttpResponse<byte[]> send(HttpRequest request, int limit) throws Exception {
        var network = client().sendAsync(request, response -> new LimitedBody(limit));
        try { return network.get(properties.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS); }
        catch (Exception e) { network.cancel(true); throw e; }
    }

    private String decodeJson(HttpResponse<byte[]> response, int expected) {
        requireStatus(response, expected);
        String type = mediaType(response).toLowerCase(java.util.Locale.ROOT);
        if (!type.equals("application/json") && !type.endsWith("+json")) throw failure(INVALID_RESPONSE);
        try {
            return validator.validate(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(response.body())).toString());
        } catch (java.nio.charset.CharacterCodingException e) { throw failure(INVALID_RESPONSE); }
    }

    private JsonNode readJson(HttpResponse<byte[]> response, int expected) {
        return json.readTree(decodeJson(response, expected));
    }

    private boolean isError(HttpResponse<byte[]> response, int status, String code) {
        if (response.statusCode() != status) return false;
        try { return code.equals(json.readTree(decodeJson(response, status)).path("error").path("code").asString("")); }
        catch (RuntimeException e) { return false; }
    }

    private String mediaType(HttpResponse<byte[]> response) {
        return response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].strip();
    }

    private void requireStatus(HttpResponse<byte[]> response, int expected) {
        int status = response.statusCode();
        if (status == expected) return;
        if (status == 408 || status == 504) throw failure(TIMEOUT);
        if (status == 429 || status >= 500) throw failure(TRANSPORT);
        throw failure(REJECTED);
    }

    private void checkDeadline(long deadline) {
        if (Thread.currentThread().isInterrupted()) throw failure(INTERRUPTED);
        if (System.nanoTime() >= deadline) throw failure(DEADLINE);
    }

    private void pause(long deadline) throws InterruptedException {
        checkDeadline(deadline);
        TimeUnit.NANOSECONDS.sleep(Math.min(properties.getPollInterval().toNanos(), Math.max(1, deadline - System.nanoTime())));
    }

    private <T> CompletableFuture<T> execute(Callable<T> action) {
        var result = new CompletableFuture<T>();
        Future<?> task = executor.submit(() -> {
            try { result.complete(action.call()); }
            catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                result.completeExceptionally(failure(reason(e)));
            }
        });
        result.orTimeout(properties.getMaxDuration().toMillis(), TimeUnit.MILLISECONDS);
        result.whenComplete((value, error) -> { if (error != null) task.cancel(true); });
        return result;
    }

    public static AnalysisGatewayException.Reason reason(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) error = error.getCause();
        if (error instanceof AnalysisGatewayException gateway) return gateway.reason();
        if (error instanceof TimeoutException || error instanceof HttpTimeoutException) return TIMEOUT;
        if (error instanceof CancellationException || error instanceof InterruptedException) return INTERRUPTED;
        return TRANSPORT;
    }

    private AnalysisGatewayException failure(AnalysisGatewayException.Reason reason) { return new AnalysisGatewayException(reason); }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
        if (client != null) client.shutdownNow();
    }

    private synchronized HttpClient client() {
        if (client == null) client = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return client;
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int maxBytes;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        private LimitedBody(int maxBytes) { this.maxBytes = maxBytes; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                int count = buffer.remaining();
                if (count > maxBytes - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new AnalysisGatewayException(INVALID_RESPONSE));
                    return;
                }
                byte[] chunk = new byte[count];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}

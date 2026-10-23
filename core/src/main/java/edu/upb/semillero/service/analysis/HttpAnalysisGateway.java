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

import static edu.upb.semillero.service.analysis.AnalysisGatewayException.Reason.*;

@Component
public class HttpAnalysisGateway implements AnalysisGateway {
    private final AnalysisProperties properties;
    private final AnalysisResultValidator validator;
    private volatile HttpClient client;

    public HttpAnalysisGateway(AnalysisProperties properties, AnalysisResultValidator validator) {
        this.properties = properties;
        this.validator = validator;
    }

    @Override
    public CompletableFuture<String> evaluate(Submission submission) {
        if (!properties.isEnabled() || !properties.isEndpointValid() || !properties.isEvaluationPathValid()) {
            return CompletableFuture.failedFuture(new AnalysisGatewayException(REJECTED));
        }
        String boundary = "Semillero" + UUID.randomUUID().toString().replace("-", "");
        var header = HttpRequest.BodyPublishers.ofString("--" + boundary
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"video.mp4\"\r\n"
                + "Content-Type: video/mp4\r\n\r\n");
        HttpRequest.BodyPublisher body;
        try {
            body = HttpRequest.BodyPublishers.concat(header, HttpRequest.BodyPublishers.ofFile(submission.original()),
                    HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n"));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(new AnalysisGatewayException(MISSING_ORIGINAL));
        }
        String base = properties.getBaseUrl().toString().replaceAll("/+$", "");
        var builder = HttpRequest.newBuilder(URI.create(base + properties.getEvaluationPath()))
                .timeout(properties.getRequestTimeout()).header("Accept", "application/json")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Analysis-Id", submission.analysisId().toString()).POST(body);
        if (properties.getToken() != null && !properties.getToken().isBlank()) {
            builder.header("Authorization", "Bearer " + properties.getToken());
        }
        CompletableFuture<HttpResponse<byte[]>> network;
        try {
            network = client().sendAsync(builder.build(), response -> new LimitedBody(properties.getMaxResponseBytes()));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(new AnalysisGatewayException(TRANSPORT));
        }
        // Own the returned Future: HttpClient's dependent Futures can propagate cancellation
        // before recording it locally. Complete this one first, then cancel the exchange.
        var result = new CompletableFuture<String>();
        network.whenComplete((response, error) -> {
            try {
                if (error != null) throw new AnalysisGatewayException(reason(error));
                result.complete(readResponse(response));
            } catch (RuntimeException e) {
                result.completeExceptionally(new AnalysisGatewayException(reason(e)));
            }
        });
        // Unlike a headers-only timeout, this bounds the complete response, including a stalled body.
        result.orTimeout(properties.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        result.whenComplete((value, error) -> {
            if (error != null) network.cancel(true);
        });
        return result;
    }

    private String readResponse(HttpResponse<byte[]> response) {
        int status = response.statusCode();
        if (status == 408 || status == 504) throw new AnalysisGatewayException(TIMEOUT);
        if (status == 429 || status >= 500) throw new AnalysisGatewayException(TRANSPORT);
        // 202 is an acknowledgement, not the final evaluation JSON expected by this contract.
        if (status != 200) throw new AnalysisGatewayException(REJECTED);
        String type = response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].strip();
        if (!type.equalsIgnoreCase("application/json") && !type.toLowerCase(java.util.Locale.ROOT).endsWith("+json")) {
            throw new AnalysisGatewayException(INVALID_RESPONSE);
        }
        try {
            String payload = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(response.body())).toString();
            return validator.validate(payload);
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new AnalysisGatewayException(INVALID_RESPONSE);
        }
    }

    static AnalysisGatewayException.Reason reason(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) {
            error = error.getCause();
        }
        if (error instanceof AnalysisGatewayException gateway) return gateway.reason();
        if (error instanceof TimeoutException || error instanceof HttpTimeoutException) return TIMEOUT;
        if (error instanceof CancellationException) return INTERRUPTED;
        return TRANSPORT;
    }

    @PreDestroy
    public void close() {
        if (client != null) client.shutdownNow();
    }

    private synchronized HttpClient client() {
        if (client == null) {
            client = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER).build();
        }
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

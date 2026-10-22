package edu.upb.semillero.service.analysis;

import edu.upb.semillero.config.AnalysisProperties;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import static edu.upb.semillero.service.analysis.AnalysisGatewayException.Reason.*;

@Component
public class HttpAnalysisGateway implements AnalysisGateway {
    private final AnalysisProperties properties;
    private final ObjectMapper json;
    private volatile HttpClient client;

    public HttpAnalysisGateway(AnalysisProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    @Override
    public Snapshot submit(Submission submission) {
        return send(submission.analysisId(), submission);
    }

    @Override
    public Snapshot getStatus(Long analysisId) {
        return send(analysisId, null);
    }

    private Snapshot send(Long id, Submission submission) {
        if (!properties.isEnabled() || !properties.isEndpointValid()) throw new AnalysisGatewayException(REJECTED);
        String base = properties.getBaseUrl().toString().replaceAll("/+$", "");
        var builder = HttpRequest.newBuilder(URI.create(base + "/internal/v1/analisis/" + id))
                .timeout(properties.getRequestTimeout()).header("Accept", "application/json");
        if (properties.getToken() != null && !properties.getToken().isBlank()) {
            builder.header("Authorization", "Bearer " + properties.getToken());
        }
        if (submission == null) builder.GET();
        else builder.header("Content-Type", "application/json").PUT(
                HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(submission)));
        var pending = client().sendAsync(builder.build(), response -> new LimitedBody());
        HttpResponse<byte[]> response;
        try {
            // Covers the entire response body as well as connection/headers.
            response = pending.get(properties.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new AnalysisGatewayException(TRANSPORT);
        } catch (java.util.concurrent.TimeoutException e) {
            pending.cancel(true);
            throw new AnalysisGatewayException(TRANSPORT);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof AnalysisGatewayException gatewayError) throw gatewayError;
            throw new AnalysisGatewayException(TRANSPORT);
        }
        int status = response.statusCode();
        if (status == 408 || status == 429 || status >= 500) throw new AnalysisGatewayException(TRANSPORT);
        if (status != 200 && !(submission != null && (status == 201 || status == 202))) {
            throw new AnalysisGatewayException(REJECTED);
        }
        try {
            return json.readValue(response.body(), Snapshot.class);
        } catch (RuntimeException e) {
            // Never forward a remote body, URL or credential in a diagnostic.
            throw new AnalysisGatewayException(INVALID_RESPONSE);
        }
    }

    @PreDestroy
    public void close() {
        if (client != null) client.shutdownNow();
    }

    private synchronized HttpClient client() {
        if (client == null) {
            client = HttpClient.newBuilder().connectTimeout(properties.getRequestTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER).build();
        }
        return client;
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private static final int MAX_BYTES = 64 * 1024;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() { return result; }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                int count = buffer.remaining();
                if (count > MAX_BYTES - bytes.size()) {
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

        @Override
        public void onError(Throwable error) { result.completeExceptionally(error); }

        @Override
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}

package edu.upb.semillero.scouting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Optional HTTPS adapters; when endpoints are absent, durable rows stay pending and are visible to operators. */
@Component
@ConditionalOnProperty(name = "scouting.workers.enabled", havingValue = "true", matchIfMissing = true)
public class ScoutingDeliveryWorkers {
    private final JdbcTemplate jdbc;
    private final ScoutingCryptoService crypto;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String eventsUrl;
    private final String eventsToken;
    private final String contactsUrl;
    private final String contactsToken;

    public ScoutingDeliveryWorkers(JdbcTemplate jdbc, ScoutingCryptoService crypto, ObjectMapper json,
            @Value("${scouting.events.url:}") String eventsUrl,
            @Value("${scouting.events.token:}") String eventsToken,
            @Value("${scouting.contacts.url:}") String contactsUrl,
            @Value("${scouting.contacts.token:}") String contactsToken) {
        this.jdbc = jdbc; this.crypto = crypto; this.json = json; this.eventsUrl = eventsUrl;
        this.eventsToken = eventsToken; this.contactsUrl = contactsUrl; this.contactsToken = contactsToken;
    }

    @Scheduled(fixedDelayString = "${scouting.events.poll-interval:PT30S}")
    public void deliverEvents() {
        if (!trustedEndpoint(eventsUrl, eventsToken)) return;
        List<Map<String,Object>> rows = jdbc.queryForList("""
                SELECT id,payload::text AS payload,idempotency_key,attempts FROM scouting_outbox
                WHERE status='PENDING' AND next_attempt_at<=now() ORDER BY created_at LIMIT 10
                """);
        for (Map<String,Object> row : rows) deliverEvent(row);
    }

    @Scheduled(fixedDelayString = "${scouting.contacts.poll-interval:PT30S}")
    public void deliverCommunications() {
        if (!trustedEndpoint(contactsUrl, contactsToken)) return;
        List<Map<String,Object>> rows = jdbc.queryForList("""
                SELECT id,prospect_id,consent_id,channel,recipient_cipher,content_cipher,idempotency_key,attempts
                FROM scouting_communications WHERE status='PENDING_CONFIGURATION' AND next_attempt_at<=now()
                ORDER BY requested_at LIMIT 10
                """);
        for (Map<String,Object> row : rows) deliverCommunication(row);
    }

    @Scheduled(fixedDelayString = "${scouting.alerts.poll-interval:PT5M}")
    public void detectRepeatedDeniedAccess() {
        List<Map<String,Object>> groups = jdbc.queryForList("""
                SELECT users_id,min(fecha) AS window_started_at,max(fecha) AS last_observed_at,count(*) AS attempts
                FROM auditoria WHERE modulo='SCOUTING' AND accion='ACCESO_DENEGADO'
                    AND users_id IS NOT NULL AND fecha>=now()-interval '15 minutes'
                GROUP BY users_id HAVING count(*)>=5
                """);
        for (Map<String,Object> group : groups) {
            jdbc.update("""
                    INSERT INTO scouting_security_alerts(alert_type,user_id,window_started_at,last_observed_at,occurrence_count)
                    VALUES('REPEATED_DENIED_ACCESS',?,?,?,?)
                    ON CONFLICT(alert_type,user_id,window_started_at) DO UPDATE
                    SET last_observed_at=EXCLUDED.last_observed_at,occurrence_count=EXCLUDED.occurrence_count
                    """, group.get("users_id"), group.get("window_started_at"), group.get("last_observed_at"), group.get("attempts"));
        }
    }

    private void deliverEvent(Map<String,Object> row) {
        try {
            HttpResponse<Void> response = post(eventsUrl, eventsToken, row.get("idempotency_key").toString(), row.get("payload").toString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                jdbc.update("UPDATE scouting_outbox SET status='PUBLISHED',published_at=now(),last_error=NULL WHERE id=? AND status='PENDING'", row.get("id"));
            } else if (response.statusCode() >= 400 && response.statusCode() < 500) {
                jdbc.update("UPDATE scouting_outbox SET status='FAILED',attempts=attempts+1,last_error=? WHERE id=? AND status='PENDING'",
                        "HTTP " + response.statusCode(), row.get("id"));
            } else retryEvent(row, "HTTP " + response.statusCode());
        } catch (Exception e) { retryEvent(row, "Endpoint no disponible"); }
    }

    private void deliverCommunication(Map<String,Object> row) {
        UUID id = UUID.fromString(row.get("id").toString());
        try {
            Object consentId = row.get("consent_id");
            if (consentId != null && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM scouting_tutor_consents WHERE id=? AND revoked_at IS NULL)", Boolean.class, consentId))) {
                jdbc.update("UPDATE scouting_communications SET status='CANCELLED',last_error='Consentimiento revocado' WHERE id=? AND status='PENDING_CONFIGURATION'", id);
                return;
            }
            JsonNode recipient = crypto.decrypt((byte[]) row.get("recipient_cipher"));
            JsonNode content = crypto.decrypt((byte[]) row.get("content_cipher"));
            Map<String,Object> message = Map.of("communicationId", id.toString(), "channel", row.get("channel"),
                    "recipient", recipient.path("address").asString(), "message", content.path("content").asString());
            String body = json.writeValueAsString(message);
            HttpResponse<Void> response = post(contactsUrl, contactsToken, row.get("id").toString(), body);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                jdbc.update("""
                        UPDATE scouting_communications SET status='SENT',sent_at=now(),attempts=attempts+1,last_error=NULL
                        WHERE id=? AND status='PENDING_CONFIGURATION'
                            AND (consent_id IS NULL OR EXISTS(SELECT 1 FROM scouting_tutor_consents WHERE id=scouting_communications.consent_id AND revoked_at IS NULL))
                        """, id);
            } else if (response.statusCode() >= 400 && response.statusCode() < 500) {
                jdbc.update("UPDATE scouting_communications SET status='DELIVERY_FAILED',attempts=attempts+1,last_error=? WHERE id=? AND status='PENDING_CONFIGURATION'",
                        "HTTP " + response.statusCode(), id);
            } else retryCommunication(row, "HTTP " + response.statusCode());
        } catch (Exception e) { retryCommunication(row, "Endpoint no disponible"); }
    }

    private HttpResponse<Void> post(String endpoint, String token, String idempotencyKey, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
    }

    private void retryEvent(Map<String,Object> row, String error) {
        int attempts = ((Number) row.get("attempts")).intValue() + 1;
        int delay = backoff(attempts);
        jdbc.update("UPDATE scouting_outbox SET attempts=attempts+1,next_attempt_at=now() + (? * interval '1 second'),last_error=? WHERE id=? AND status='PENDING'",
                delay, error, row.get("id"));
    }

    private void retryCommunication(Map<String,Object> row, String error) {
        int attempts = ((Number) row.get("attempts")).intValue() + 1;
        int delay = backoff(attempts);
        jdbc.update("UPDATE scouting_communications SET attempts=attempts+1,next_attempt_at=now() + (? * interval '1 second'),last_error=? WHERE id=? AND status='PENDING_CONFIGURATION'",
                delay, error, row.get("id"));
    }

    private static int backoff(int attempt) { return Math.min(3600, 15 * (1 << Math.min(8, Math.max(0, attempt - 1)))); }

    private static boolean trustedEndpoint(String endpoint, String token) {
        if (endpoint == null || endpoint.isBlank()) return false;
        try {
            URI uri = URI.create(endpoint);
            boolean loopback = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()) || "::1".equals(uri.getHost());
            return ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && token != null && !token.isBlank())
                    || loopback && "http".equalsIgnoreCase(uri.getScheme());
        } catch (RuntimeException e) { return false; }
    }
}

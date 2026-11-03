package edu.upb.semillero.scouting;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Service
@Transactional
public class ScoutingContactService {
    private final ScoutingSupport support;
    private final JdbcTemplate jdbc;

    ScoutingContactService(ScoutingSupport support) {
        this.support = support; this.jdbc = support.jdbc();
    }

    public Map<String,Object> grantConsent(UUID prospectId, Map<String,Object> request) {
        support.require("RESPONSABLE_SALVAGUARDA"); support.requireAccess(prospectId);
        Map<String,Object> profile = profile(prospectId, "TUTOR_CONSENT");
        LocalDate dob = LocalDate.parse(profile.get("birthDate").toString());
        if (!dob.plusYears(18).isAfter(LocalDate.now())) ScoutingProspectService.conflict("El prospecto es adulto; no se registra consentimiento de tutor");
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM scouting_tutor_consents WHERE prospect_id=? AND revoked_at IS NULL)", Boolean.class, prospectId))) ScoutingProspectService.conflict("Ya existe un consentimiento vigente; revócalo antes de registrar otro");
        Map<String,Object> tutor = new LinkedHashMap<>();
        tutor.put("name", ScoutingSupport.text(request, "tutorName", true));
        tutor.put("relationship", ScoutingSupport.text(request, "relationship", true));
        tutor.put("email", ScoutingSupport.text(request, "tutorEmail", false));
        tutor.put("phone", ScoutingSupport.text(request, "tutorPhone", false));
        if (tutor.get("email") == null && tutor.get("phone") == null) ScoutingProspectService.bad("Se requiere correo o teléfono del tutor");
        String channel = ScoutingSupport.text(request, "channel", true);
        if (!List.of("IN_PERSON", "EMAIL", "PHONE", "DOCUMENT", "PORTAL").contains(channel)) ScoutingProspectService.bad("channel no válido");
        OffsetDateTime grantedAt = ScoutingProspectService.parseTimestamp(request.get("grantedAt"), "grantedAt");
        if (grantedAt == null || grantedAt.isAfter(OffsetDateTime.now())) ScoutingProspectService.bad("grantedAt debe tener fecha válida no futura");
        String evidence = requiredEvidenceReference(request);
        UUID id = jdbc.queryForObject("""
                INSERT INTO scouting_tutor_consents(prospect_id,tutor_payload_cipher,channel,evidence_reference,granted_at,granted_by)
                VALUES(?,?,?,?,?,?) RETURNING id
                """, UUID.class, prospectId, support.crypto().encrypt(tutor), channel, evidence, grantedAt, support.userId());
        support.audit("TUTOR_CONSENT_GRANTED", "CONSENT", id, Map.of("prospectId", prospectId.toString(), "channel", channel));
        return consentStatus(id);
    }

    public Map<String,Object> revokeConsent(UUID prospectId, UUID consentId, Map<String,Object> request) {
        support.require("RESPONSABLE_SALVAGUARDA"); support.requireAccess(prospectId);
        String reason = ScoutingSupport.text(request, "reason", true);
        if (reason.length() < 8) ScoutingProspectService.bad("reason debe tener al menos 8 caracteres");
        Map<String,Object> consent = support.one("SELECT id,revoked_at FROM scouting_tutor_consents WHERE id=? AND prospect_id=?", consentId, prospectId);
        if (consent.get("revoked_at") != null) return consentStatus(consentId);
        jdbc.update("UPDATE scouting_tutor_consents SET revoked_at=now(),revoked_by=? WHERE id=?", support.userId(), consentId);
        int cancelled = jdbc.update("UPDATE scouting_communications SET status='CANCELLED',last_error='Consentimiento revocado' WHERE prospect_id=? AND consent_id=? AND status='PENDING_CONFIGURATION'", prospectId, consentId);
        support.audit("TUTOR_CONSENT_REVOKED", "CONSENT", consentId, Map.of("prospectId", prospectId.toString(), "pendingContactsCancelled", cancelled));
        return consentStatus(consentId);
    }

    public List<Map<String,Object>> consents(UUID prospectId) {
        support.require("RESPONSABLE_SALVAGUARDA", "COORDINADOR_SCOUTING", "SCOUTING_STATUS");
        if (!support.hasRole("SCOUTING_STATUS")) support.requireAccess(prospectId);
        List<Map<String,Object>> results = jdbc.queryForList("""
                SELECT id,prospect_id,channel,granted_at,granted_by,evidence_reference,revoked_at,created_at,
                    CASE WHEN revoked_at IS NULL THEN 'ACTIVE' ELSE 'REVOKED' END AS status
                FROM scouting_tutor_consents WHERE prospect_id=? ORDER BY granted_at DESC
                """, prospectId);
        for (Map<String,Object> result : results) {
            UUID consentId = UUID.fromString(result.get("id").toString());
            support.audit("CONSENT_STATUS_VIEWED", "CONSENT", consentId, Map.of("purpose", "SAFEGUARDING_STATUS"));
            if (support.hasRole("RESPONSABLE_SALVAGUARDA")) {
                byte[] encrypted = (byte[]) support.one("SELECT tutor_payload_cipher FROM scouting_tutor_consents WHERE id=?", consentId).get("tutor_payload_cipher");
                result.put("tutor", support.crypto().decrypt(encrypted));
            } else {
                result.remove("granted_by");
                result.remove("evidence_reference");
            }
        }
        return results;
    }

    public Map<String,Object> createCommunication(UUID prospectId, Map<String,Object> request, String headerKey) {
        support.require("RECLUTADOR", "COORDINADOR_SCOUTING"); support.requireAccess(prospectId);
        Map<String,Object> profile = profile(prospectId, "AUTHORIZED_COMMUNICATION");
        String stage = support.one("SELECT stage FROM scouting_prospects WHERE id=?", prospectId).get("stage").toString();
        if (List.of("ARCHIVED", "DISCARDED", "INCORPORATED").contains(stage)) ScoutingProspectService.conflict("El prospecto no admite nuevos contactos en su etapa actual");
        boolean minor = LocalDate.parse(profile.get("birthDate").toString()).plusYears(18).isAfter(LocalDate.now());
        String channel = ScoutingSupport.text(request, "channel", true);
        if (!List.of("EMAIL", "PHONE").contains(channel)) ScoutingProspectService.bad("channel debe ser EMAIL o PHONE");
        String templateCode = ScoutingSupport.text(request, "templateCode", true);
        String key = headerKey == null || headerKey.isBlank() ? ScoutingSupport.text(request, "idempotencyKey", true) : headerKey.trim();
        if (key.length() > 120) ScoutingProspectService.bad("Idempotency-Key supera 120 caracteres");
        String audience = minor ? "TUTOR" : "ADULT";
        Map<String,Object> template = support.one("SELECT id,body,audience FROM scouting_message_templates WHERE code=? AND approved ORDER BY version DESC LIMIT 1", templateCode);
        if (!audience.equals(template.get("audience"))) ScoutingProspectService.conflict("La plantilla aprobada no corresponde al destinatario requerido");
        Map<String,Object> recipient;
        UUID consentId = null;
        Map<String,Object> placeholders = new LinkedHashMap<>();
        placeholders.put("nombre", profile.get("firstName"));
        placeholders.put("nombre_jugador", profile.get("firstName"));
        if (minor) {
            var consents = jdbc.queryForList("SELECT id,tutor_payload_cipher FROM scouting_tutor_consents WHERE prospect_id=? AND revoked_at IS NULL AND granted_at<=now() ORDER BY granted_at DESC LIMIT 1", prospectId);
            if (consents.isEmpty()) ScoutingProspectService.conflict("No se puede contactar a un menor sin consentimiento vigente de tutor");
            consentId = UUID.fromString(consents.getFirst().get("id").toString());
            JsonNode tutor = support.crypto().decrypt((byte[]) consents.getFirst().get("tutor_payload_cipher"));
            String address = tutor.path("EMAIL".equals(channel) ? "email" : "phone").asString(null);
            if (address == null || address.isBlank()) ScoutingProspectService.conflict("El consentimiento vigente no contiene un contacto para el canal solicitado");
            placeholders.put("nombre_tutor", tutor.path("name").asString());
            recipient = Map.of("address", address, "channel", channel);
        } else {
            String field = "EMAIL".equals(channel) ? "email" : "phone";
            String address = profile.get(field) == null ? null : profile.get(field).toString();
            if (address == null || address.isBlank()) ScoutingProspectService.conflict("No hay dato de contacto registrado para ese canal");
            recipient = Map.of("address", address, "channel", channel);
        }
        String content = render(template.get("body").toString(), placeholders);
        String requestFingerprint = support.requestFingerprint(request);
        var existing = jdbc.queryForList("SELECT id,request_fingerprint FROM scouting_communications WHERE requested_by=? AND idempotency_key=?", support.userId(), key);
        if (!existing.isEmpty()) {
            if (!requestFingerprint.equals(existing.getFirst().get("request_fingerprint"))) ScoutingProspectService.conflict("Idempotency-Key ya fue usado con datos diferentes");
            return communication(UUID.fromString(existing.getFirst().get("id").toString()));
        }
        UUID id = jdbc.queryForList("""
                INSERT INTO scouting_communications(prospect_id,consent_id,template_id,channel,recipient_cipher,content_cipher,
                    content_fingerprint,status,requested_by,idempotency_key,request_fingerprint)
                VALUES(?,?,?,?,?,?,?,'PENDING_CONFIGURATION',?,?,?) ON CONFLICT(requested_by,idempotency_key) DO NOTHING RETURNING id
                """, UUID.class, prospectId, consentId, template.get("id"), channel,
                support.crypto().encrypt(recipient), support.crypto().encrypt(Map.of("content", content)),
                support.crypto().fingerprint(content), support.userId(), key, requestFingerprint).stream().findFirst().orElse(null);
        if (id == null) {
            Map<String,Object> row = support.one("SELECT id,request_fingerprint FROM scouting_communications WHERE requested_by=? AND idempotency_key=?", support.userId(), key);
            if (!requestFingerprint.equals(row.get("request_fingerprint"))) ScoutingProspectService.conflict("Idempotency-Key ya fue usado con datos diferentes");
            return communication(UUID.fromString(row.get("id").toString()));
        }
        support.audit("COMMUNICATION_QUEUED", "COMMUNICATION", id, Map.of("prospectId", prospectId.toString(), "channel", channel));
        return communication(id);
    }

    public List<Map<String,Object>> communications(UUID prospectId) {
        support.require("RECLUTADOR", "COORDINADOR_SCOUTING", "RESPONSABLE_SALVAGUARDA", "SCOUTING_STATUS");
        if (!support.hasRole("SCOUTING_STATUS")) support.requireAccess(prospectId);
        // This endpoint never returns the encrypted or rendered message, recipient, or message body.
        List<Map<String,Object>> results = jdbc.queryForList("""
                SELECT c.id,c.prospect_id,c.template_id,t.code AS template_code,c.channel,c.status,c.requested_at,c.sent_at,c.attempts,c.last_error
                FROM scouting_communications c JOIN scouting_message_templates t ON t.id=c.template_id
                WHERE c.prospect_id=? ORDER BY c.requested_at DESC
                """, prospectId);
        for (Map<String,Object> result : results) {
            support.audit("COMMUNICATION_STATUS_VIEWED", "COMMUNICATION", result.get("id"), Map.of("purpose", "CONTACT_STATUS"));
        }
        return results;
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> templates() {
        support.require("RECLUTADOR", "COORDINADOR_SCOUTING", "SCOUTING_CONFIG");
        return jdbc.queryForList("SELECT id,code,version,audience,body,approved,created_at FROM scouting_message_templates ORDER BY code,version DESC");
    }

    public Map<String,Object> createTemplate(Map<String,Object> request) {
        support.require("COORDINADOR_SCOUTING", "SCOUTING_CONFIG");
        String code = ScoutingSupport.text(request, "code", true);
        String audience = ScoutingSupport.text(request, "audience", true);
        String body = ScoutingSupport.text(request, "body", true);
        if (code.length() > 80) ScoutingProspectService.bad("code supera 80 caracteres");
        if (!List.of("ADULT", "TUTOR").contains(audience)) ScoutingProspectService.bad("audience debe ser ADULT o TUTOR");
        if (body.length() > 2000 || body.length() < 10) ScoutingProspectService.bad("body debe tener entre 10 y 2000 caracteres");
        int version = jdbc.queryForObject("SELECT coalesce(max(version),0)+1 FROM scouting_message_templates WHERE code=?", Integer.class, code);
        UUID id = jdbc.queryForObject("INSERT INTO scouting_message_templates(code,version,audience,body,approved,created_by) VALUES(?,?,?,?,false,?) RETURNING id",
                UUID.class, code, version, audience, body, support.userId());
        support.audit("MESSAGE_TEMPLATE_CREATED", "MESSAGE_TEMPLATE", id, Map.of("code", code, "version", version));
        return support.one("SELECT id,code,version,audience,body,approved,created_at FROM scouting_message_templates WHERE id=?", id);
    }

    public Map<String,Object> approveTemplate(UUID id) {
        support.require("RESPONSABLE_SALVAGUARDA", "COORDINADOR_SCOUTING");
        Map<String,Object> template = support.one("SELECT id,code,version,approved FROM scouting_message_templates WHERE id=?", id);
        if (Boolean.TRUE.equals(template.get("approved"))) return template;
        jdbc.update("UPDATE scouting_message_templates SET approved=true WHERE id=?", id);
        support.audit("MESSAGE_TEMPLATE_APPROVED", "MESSAGE_TEMPLATE", id, Map.of("code", template.get("code")));
        return support.one("SELECT id,code,version,audience,body,approved,created_at FROM scouting_message_templates WHERE id=?", id);
    }

    public Map<String,Object> retryCommunication(UUID id) {
        support.require("COORDINADOR_SCOUTING", "RESPONSABLE_SALVAGUARDA");
        Map<String,Object> row = support.one("SELECT id,prospect_id,consent_id,status FROM scouting_communications WHERE id=?", id);
        support.requireAccess(UUID.fromString(row.get("prospect_id").toString()));
        if (!"DELIVERY_FAILED".equals(row.get("status"))) ScoutingProspectService.conflict("Solo se reintentan comunicaciones DELIVERY_FAILED");
        if (row.get("consent_id") != null && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM scouting_tutor_consents WHERE id=? AND revoked_at IS NULL)", Boolean.class, row.get("consent_id")))) {
            ScoutingProspectService.conflict("No se reintenta: el consentimiento está revocado");
        }
        jdbc.update("UPDATE scouting_communications SET status='PENDING_CONFIGURATION',attempts=0,next_attempt_at=now(),last_error=NULL WHERE id=?", id);
        support.audit("COMMUNICATION_RETRY_REQUESTED", "COMMUNICATION", id, Map.of());
        return communication(id);
    }

    private Map<String,Object> profile(UUID prospectId, String purpose) {
        support.requireAccess(prospectId);
        Map<String,Object> prospect = support.one("SELECT sensitive_payload,anonymized_at FROM scouting_prospects WHERE id=?", prospectId);
        if (prospect.get("anonymized_at") != null) ScoutingProspectService.conflict("Los datos personales del prospecto ya fueron anonimizados");
        JsonNode payload = support.crypto().decrypt((byte[]) prospect.get("sensitive_payload"));
        support.audit("PROSPECT_CONTACT_DATA_READ", "PROSPECT", prospectId, Map.of("purpose", purpose));
        Map<String,Object> values = new LinkedHashMap<>();
        for (String key : List.of("firstName","lastName","birthDate","email","phone")) {
            JsonNode value = payload.path(key); values.put(key, value.isMissingNode() || value.isNull() ? null : value.asString());
        }
        return values;
    }

    private Map<String,Object> consentStatus(UUID id) {
        return support.one("""
                SELECT id,prospect_id,channel,granted_at,revoked_at,created_at,
                    CASE WHEN revoked_at IS NULL THEN 'ACTIVE' ELSE 'REVOKED' END AS status
                FROM scouting_tutor_consents WHERE id=?
                """, id);
    }

    private Map<String,Object> communication(UUID id) {
        return support.one("SELECT id,prospect_id,channel,status,requested_at,sent_at,attempts,last_error FROM scouting_communications WHERE id=?", id);
    }

    private static String render(String template, Map<String,Object> placeholders) {
        String content = template;
        for (var value : placeholders.entrySet()) {
            content = content.replace("{{" + value.getKey() + "}}", value.getValue() == null ? "" : value.getValue().toString());
        }
        if (content.contains("{{")) ScoutingProspectService.conflict("La plantilla aprobada contiene variables no disponibles");
        return content;
    }

    static String requiredEvidenceReference(Map<String,Object> request) {
        String evidence = ScoutingSupport.text(request, "evidenceReference", true);
        if (evidence.length() > 500) ScoutingProspectService.bad("evidenceReference supera 500 caracteres");
        return evidence;
    }
}

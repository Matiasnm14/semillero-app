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
public class ScoutingWorkflowService {
    private final ScoutingSupport support;
    private final JdbcTemplate jdbc;

    ScoutingWorkflowService(ScoutingSupport support) {
        this.support = support;
        this.jdbc = support.jdbc();
    }

    public Map<String,Object> requestStage(UUID id, Map<String,Object> request) {
        support.require("COORDINADOR_SCOUTING", "RECLUTADOR", "EVALUADOR"); support.requireAccess(id);
        String target = ScoutingSupport.text(request, "stage", true);
        String reason = ScoutingSupport.text(request, "reason", true);
        checkReason(reason);
        if (!ScoutingProspectService.validStage(target)) ScoutingProspectService.bad("stage no válido");
        Map<String,Object> prospect = support.one("SELECT stage,pending_stage,version,anonymized_at,merged_into_prospect_id FROM scouting_prospects WHERE id=?", id);
        if (prospect.get("anonymized_at") != null) conflict("Un prospecto anonimizado no puede reabrirse");
        if (prospect.get("merged_into_prospect_id") != null) conflict("Un duplicado consolidado no puede reabrirse");
        String from = prospect.get("stage").toString();
        if (prospect.get("pending_stage") != null) conflict("El prospecto ya tiene una decisión pendiente de segunda revisión");
        if (!allowed(from, target)) conflict("Transición no permitida: " + from + " → " + target);
        String evidence = null;
        if ("PRESELECTED".equals(target) && ("ARCHIVED".equals(from) || "DISCARDED".equals(from))) {
            evidence = ScoutingSupport.text(request, "newEvidenceReference", true);
            if (evidence.length() > 500) ScoutingProspectService.bad("newEvidenceReference supera 500 caracteres");
        }
        validateRequirements(id, from, target);
        if ("RECOMMENDED".equals(target) || "DISCARDED".equals(target)) {
            jdbc.update("UPDATE scouting_prospects SET pending_stage=?,pending_reason=?,pending_requested_by=?,version=version+1,updated_at=now() WHERE id=?",
                    target, reason, support.userId(), id);
            support.audit("STAGE_REVIEW_REQUESTED", "PROSPECT", id, Map.of("from", from, "to", target));
            return Map.of("prospectId", id, "stage", from, "pendingStage", target, "requiresSecondReviewer", true);
        }
        applyStage(id, from, target, reason, support.userId(), null, evidence);
        return support.prospect(id, true);
    }

    public Map<String,Object> approveStage(UUID id, Map<String,Object> request) {
        support.require("COORDINADOR_SCOUTING", "RECLUTADOR"); support.requireAccess(id);
        Map<String,Object> prospect = support.one("SELECT stage,pending_stage,pending_reason,pending_requested_by FROM scouting_prospects WHERE id=?", id);
        if (prospect.get("pending_stage") == null) conflict("No hay una decisión pendiente");
        long requestedBy = ((Number) prospect.get("pending_requested_by")).longValue();
        if (requestedBy == support.userId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La segunda revisión debe hacerla otra persona");
        if (request.get("approve") instanceof Boolean approved && !approved) {
            String reason = ScoutingSupport.text(request, "reason", true); checkReason(reason);
            jdbc.update("UPDATE scouting_prospects SET pending_stage=NULL,pending_reason=NULL,pending_requested_by=NULL,version=version+1,updated_at=now() WHERE id=?", id);
            jdbc.update("INSERT INTO scouting_stage_history(prospect_id,from_stage,to_stage,reason,changed_by,reviewed_by) VALUES(?,?,?, ?,?,?)",
                    id, prospect.get("stage"), prospect.get("stage"), "Revisión rechazada: " + reason, support.userId(), support.userId());
            support.audit("STAGE_REVIEW_REJECTED", "PROSPECT", id, Map.of());
            return support.prospect(id, true);
        }
        String target = prospect.get("pending_stage").toString();
        validateRequirements(id, prospect.get("stage").toString(), target);
        String reason = prospect.get("pending_reason").toString();
        applyStage(id, prospect.get("stage").toString(), target, reason, requestedBy, support.userId(), null);
        return support.prospect(id, true);
    }

    private void applyStage(UUID id, String from, String target, String reason, Long changedBy, Long reviewer, String evidence) {
        int updated = jdbc.update("""
                UPDATE scouting_prospects SET stage=?,pending_stage=NULL,pending_reason=NULL,pending_requested_by=NULL,
                    version=version+1,updated_at=now() WHERE id=? AND stage=?
                """, target, id, from);
        if (updated != 1) conflict("El prospecto cambió en otra operación; vuelve a cargarlo");
        if ("ARCHIVED".equals(target)) {
            jdbc.update("UPDATE scouting_assignments SET active=false,completed_at=coalesce(completed_at,now()) WHERE prospect_id=? AND active", id);
            jdbc.update("UPDATE scouting_communications SET status='CANCELLED',last_error='Prospecto archivado' WHERE prospect_id=? AND status='PENDING_CONFIGURATION'", id);
            jdbc.update("UPDATE scouting_trials SET status='CANCELLED' WHERE prospect_id=? AND status='SCHEDULED'", id);
        }
        jdbc.update("INSERT INTO scouting_stage_history(prospect_id,from_stage,to_stage,reason,changed_by,reviewed_by,evidence_reference) VALUES(?,?,?,?,?,?,?)",
                id, from, target, reason, changedBy, reviewer, evidence);
        if ("ACCEPTED".equals(target)) enqueueAcceptedEvent(id);
        support.audit("STAGE_CHANGED", "PROSPECT", id, Map.of("from", from, "to", target));
    }

    private void validateRequirements(UUID id, String from, String target) {
        if ("PRESELECTED".equals(target) && Boolean.TRUE.equals(jdbc.queryForObject("SELECT duplicate_review_status='NEEDS_REVIEW' FROM scouting_prospects WHERE id=?", Boolean.class, id))) {
            conflict("Revisa posibles duplicados antes de preseleccionar");
        }
        if ("OBSERVED".equals(target) && !exists("SELECT EXISTS(SELECT 1 FROM scouting_observation_reports WHERE prospect_id=?)", id)) conflict("Se requiere al menos una observación sincronizada");
        if ("EVALUATED".equals(target) && !exists("SELECT EXISTS(SELECT 1 FROM scouting_observation_reports WHERE prospect_id=?)", id)) conflict("Se requiere una observación antes de evaluar");
        if ("CONTACTED".equals(target) && isMinor(id) && !hasCurrentConsent(id)) conflict("No se puede contactar a un menor sin consentimiento vigente de tutor");
        if ("CONTACTED".equals(target) && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM scouting_communications WHERE prospect_id=? AND status='SENT')", Boolean.class, id))) conflict("No se marca CONTACTED hasta que el canal institucional acepte un mensaje autorizado");
        if ("TRIAL".equals(target) && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM scouting_trials WHERE prospect_id=? AND status='SCHEDULED' AND invitation_accepted)", Boolean.class, id))) conflict("La prueba requiere invitación aceptada y fecha programada");
        if ("ACCEPTED".equals(target) && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM scouting_trials WHERE prospect_id=? AND status='COMPLETED' AND outcome='ACCEPT')", Boolean.class, id))) conflict("La aceptación requiere una prueba finalizada con resultado favorable");
        if ("INCORPORATED".equals(target) && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM scouting_player_links WHERE prospect_id=?)", Boolean.class, id))) conflict("Vincula primero el jugador existente de la ficha técnica");
    }

    private boolean isMinor(UUID id) {
        JsonNode profile = support.crypto().decrypt((byte[]) support.one("SELECT sensitive_payload FROM scouting_prospects WHERE id=?", id).get("sensitive_payload"));
        LocalDate dob = LocalDate.parse(profile.path("birthDate").asString());
        return dob.plusYears(18).isAfter(LocalDate.now());
    }

    private boolean hasCurrentConsent(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM scouting_tutor_consents WHERE prospect_id=? AND revoked_at IS NULL AND granted_at<=now())", Boolean.class, id));
    }

    public List<Map<String,Object>> createTrials(Map<String,Object> request) {
        support.require("COORDINADOR_SCOUTING", "RECLUTADOR");
        UUID prospectId = ScoutingSupport.uuid(ScoutingSupport.text(request, "prospectId", true), "prospectId");
        support.requireAccess(prospectId);
        if (!"CONTACTED".equals(support.one("SELECT stage FROM scouting_prospects WHERE id=?", prospectId).get("stage"))) conflict("La prueba se agenda desde la etapa CONTACTED");
        if (isMinor(prospectId) && !hasCurrentConsent(prospectId)) conflict("El consentimiento del tutor debe estar vigente para agendar");
        if (!Boolean.TRUE.equals(request.get("invitationAccepted"))) conflict("Registra que la invitación fue aceptada antes de agendar");
        OffsetDateTime scheduled = ScoutingProspectService.parseTimestamp(request.get("scheduledAt"), "scheduledAt");
        if (scheduled == null || scheduled.isBefore(OffsetDateTime.now().minusMinutes(5))) ScoutingProspectService.bad("scheduledAt debe ser una fecha futura");
        String location = ScoutingSupport.text(request, "location", true);
        if (location.length() > 240) ScoutingProspectService.bad("location supera 240 caracteres");
        Long clubId = ScoutingProspectService.positiveLong(request.get("clubId"), "clubId", false);
        if (clubId != null) support.one("SELECT id FROM clubes WHERE id=?", clubId);
        long evaluatorId = ScoutingProspectService.positiveLong(request.get("evaluatorUserId"), "evaluatorUserId", true);
        support.one("SELECT id FROM users WHERE id=?", evaluatorId);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM usuarios_roles ur JOIN roles r ON r.id=ur.roles_id WHERE ur.users_id=? AND r.codigo='EVALUADOR')", Boolean.class, evaluatorId))) {
            conflict("El usuario asignado debe tener el rol EVALUADOR");
        }
        UUID trialId = jdbc.queryForObject("""
                INSERT INTO scouting_trials(prospect_id,scheduled_at,location,club_id,evaluator_user_id,invitation_accepted,created_by)
                VALUES(?,?,?,?,?,true,?) RETURNING id
                """, UUID.class, prospectId, scheduled, location, clubId, evaluatorId, support.userId());
        support.audit("TRIAL_SCHEDULED", "TRIAL", trialId, Map.of("prospectId", prospectId.toString()));
        return jdbc.queryForList("SELECT id,prospect_id,scheduled_at,location,club_id,evaluator_user_id,status,invitation_accepted,created_at FROM scouting_trials WHERE id=?", trialId);
    }

    public Map<String,Object> completeTrial(UUID trialId, Map<String,Object> request) {
        support.require("COORDINADOR_SCOUTING", "RECLUTADOR", "EVALUADOR");
        Map<String,Object> trial = support.one("SELECT id,prospect_id,evaluator_user_id,status FROM scouting_trials WHERE id=?", trialId);
        UUID prospectId = UUID.fromString(trial.get("prospect_id").toString()); support.requireAccess(prospectId);
        if (!"SCHEDULED".equals(trial.get("status"))) conflict("La prueba ya fue cerrada");
        if (!"TRIAL".equals(support.one("SELECT stage FROM scouting_prospects WHERE id=?", prospectId).get("stage"))) conflict("El resultado se registra cuando el prospecto ya está en etapa TRIAL");
        if (!support.hasRole("COORDINADOR_SCOUTING") && ((Number) trial.get("evaluator_user_id")).longValue() != support.userId()) ScoutingProspectService.forbidden();
        String outcome = ScoutingSupport.text(request, "outcome", true);
        if (!List.of("ACCEPT","REJECT","REASSESS").contains(outcome)) ScoutingProspectService.bad("outcome no válido");
        String reason = ScoutingSupport.text(request, "reason", true); checkReason(reason);
        jdbc.update("UPDATE scouting_trials SET status='COMPLETED',outcome=?,outcome_reason=?,completed_at=now() WHERE id=?", outcome, reason, trialId);
        support.audit("TRIAL_COMPLETED", "TRIAL", trialId, Map.of("outcome", outcome));
        return support.one("SELECT id,prospect_id,scheduled_at,location,club_id,evaluator_user_id,status,outcome,outcome_reason,completed_at FROM scouting_trials WHERE id=?", trialId);
    }

    public Map<String,Object> linkPlayer(UUID prospectId, Map<String,Object> request) {
        support.require("COORDINADOR_SCOUTING", "RECLUTADOR"); support.requireAccess(prospectId);
        if (!"ACCEPTED".equals(support.one("SELECT stage FROM scouting_prospects WHERE id=?", prospectId).get("stage"))) conflict("Solo un prospecto aceptado se vincula a la ficha técnica");
        long playerId = ScoutingProspectService.positiveLong(request.get("playerId"), "playerId", true);
        JsonNode profile = support.crypto().decrypt((byte[]) support.one("SELECT sensitive_payload FROM scouting_prospects WHERE id=?", prospectId).get("sensitive_payload"));
        Map<String,Object> player;
        try {
            player = support.one("""
                    SELECT j.id,j.fecha_nacimiento,u.nombres,u.apellidos FROM jugadores j
                    LEFT JOIN users u ON u.id=j.users_id WHERE j.id=?
                    """, playerId);
        } catch (ResponseStatusException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe ese jugador en la ficha técnica"); }
        String fullProspect = ScoutingSupport.normalized(profile.path("firstName").asString()) + ScoutingSupport.normalized(profile.path("lastName").asString());
        String fullPlayer = ScoutingSupport.normalized((String) player.get("nombres")) + ScoutingSupport.normalized((String) player.get("apellidos"));
        Object dobRaw = player.get("fecha_nacimiento");
        if (!fullProspect.equals(fullPlayer) || dobRaw == null || !LocalDate.parse(profile.path("birthDate").asString()).equals(dobRaw instanceof LocalDate date ? date : LocalDate.parse(dobRaw.toString()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La identidad del jugador no coincide; requiere revisión manual antes del vínculo");
        }
        var current = jdbc.queryForList("SELECT player_id FROM scouting_player_links WHERE prospect_id=?", prospectId);
        if (!current.isEmpty()) {
            if (((Number) current.getFirst().get("player_id")).longValue() == playerId) return Map.of("prospectId", prospectId, "playerId", playerId, "alreadyLinked", true);
            conflict("El prospecto ya está vinculado a otro jugador");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM scouting_player_links WHERE player_id=?)", Boolean.class, playerId))) conflict("El jugador ya está vinculado a otro prospecto");
        jdbc.update("INSERT INTO scouting_player_links(prospect_id,player_id,linked_by) VALUES(?,?,?)", prospectId, playerId, support.userId());
        jdbc.update("UPDATE scouting_prospects SET stage='INCORPORATED',version=version+1,updated_at=now() WHERE id=? AND stage='ACCEPTED'", prospectId);
        jdbc.update("INSERT INTO scouting_stage_history(prospect_id,from_stage,to_stage,reason,changed_by) VALUES(?, 'ACCEPTED','INCORPORATED',?,?)",
                prospectId, "Vínculo idempotente con jugador existente " + playerId, support.userId());
        support.audit("PLAYER_LINKED", "PROSPECT", prospectId, Map.of("playerId", playerId));
        return Map.of("prospectId", prospectId, "playerId", playerId, "alreadyLinked", false);
    }

    public List<Map<String,Object>> grants(Map<String,Object> request) {
        support.require("COORDINADOR_SCOUTING", "SCOUTING_CONFIG");
        long userId = ScoutingProspectService.positiveLong(request.get("userId"), "userId", true);
        support.one("SELECT id FROM users WHERE id=?", userId);
        String category = ScoutingSupport.text(request, "category", false);
        String site = ScoutingSupport.text(request, "site", false);
        if (category == null && site == null) ScoutingProspectService.bad("La asignación debe limitarse por categoría o sede");
        if (category != null && category.length() > 80 || site != null && site.length() > 120) ScoutingProspectService.bad("category o site exceden la longitud permitida");
        long id = jdbc.queryForObject("INSERT INTO scouting_user_scopes(user_id,category,site,granted_by) VALUES(?,?,?,?) RETURNING id", Long.class,
                userId, category, site, support.userId());
        support.audit("SCOPE_GRANTED", "USER_SCOPE", id, Map.of("userId", userId));
        return jdbc.queryForList("SELECT id,user_id,category,site,granted_by,granted_at,active FROM scouting_user_scopes WHERE id=?", id);
    }

    public Map<String,Object> revokeScope(long id) {
        support.require("COORDINADOR_SCOUTING");
        int changed = jdbc.update("UPDATE scouting_user_scopes SET active=false,revoked_by=?,revoked_at=now() WHERE id=? AND active", support.userId(), id);
        if (changed == 0 && jdbc.queryForList("SELECT id FROM scouting_user_scopes WHERE id=?", id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe ese alcance");
        }
        support.audit("SCOPE_REVOKED", "USER_SCOPE", id, Map.of());
        return support.one("SELECT id,user_id,category,site,granted_by,granted_at,active,revoked_by,revoked_at FROM scouting_user_scopes WHERE id=?", id);
    }

    public List<Map<String,Object>> auditEvents(String resourceType, String resourceId, int limit) {
        support.require("SCOUTING_AUDITOR", "COORDINADOR_SCOUTING");
        if (limit < 1 || limit > 200) ScoutingProspectService.bad("limit debe estar entre 1 y 200");
        StringBuilder sql = new StringBuilder("SELECT id,actor_user_id,action,resource_type,resource_id,occurred_at,metadata FROM scouting_audit_events WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        if (resourceType != null) { sql.append(" AND resource_type=?"); args.add(resourceType); }
        if (resourceId != null) { sql.append(" AND resource_id=?"); args.add(resourceId); }
        sql.append(" ORDER BY occurred_at DESC,id DESC LIMIT ?"); args.add(limit);
        List<Map<String,Object>> results = jdbc.queryForList(sql.toString(), args.toArray());
        support.audit("AUDIT_EVENTS_VIEWED", "AUDIT", null,
                Map.of("purpose", "SECURITY_REVIEW", "resultCount", results.size(), "limit", limit));
        return results;
    }

    public List<Map<String,Object>> outbox(int limit) {
        support.require("COORDINADOR_SCOUTING", "SCOUTING_CONFIG");
        if (limit < 1 || limit > 200) ScoutingProspectService.bad("limit debe estar entre 1 y 200");
        List<Map<String,Object>> results = jdbc.queryForList("SELECT id,event_type,aggregate_id,idempotency_key,status,attempts,next_attempt_at,created_at,published_at,last_error FROM scouting_outbox ORDER BY created_at DESC LIMIT ?", limit);
        support.audit("OUTBOX_VIEWED", "OUTBOX", null, Map.of("resultCount", results.size(), "limit", limit));
        return results;
    }

    public Map<String,Object> retryOutbox(UUID id) {
        support.require("COORDINADOR_SCOUTING", "SCOUTING_CONFIG");
        int changed = jdbc.update("UPDATE scouting_outbox SET status='PENDING',attempts=0,next_attempt_at=now(),last_error=NULL WHERE id=? AND status='FAILED'", id);
        if (changed == 0) conflict("El evento no existe o no está en estado FAILED");
        support.audit("OUTBOX_RETRY_REQUESTED", "OUTBOX", id, Map.of());
        return support.one("SELECT id,event_type,aggregate_id,status,attempts,next_attempt_at FROM scouting_outbox WHERE id=?", id);
    }

    public List<Map<String,Object>> securityAlerts(int limit) {
        support.require("SCOUTING_AUDITOR", "COORDINADOR_SCOUTING");
        if (limit < 1 || limit > 200) ScoutingProspectService.bad("limit debe estar entre 1 y 200");
        List<Map<String,Object>> results = jdbc.queryForList("""
                SELECT id,alert_type,user_id,window_started_at,last_observed_at,occurrence_count,acknowledged_at,acknowledged_by
                FROM scouting_security_alerts ORDER BY acknowledged_at NULLS FIRST,last_observed_at DESC LIMIT ?
                """, limit);
        support.audit("SECURITY_ALERTS_VIEWED", "SECURITY_ALERT", null, Map.of("resultCount", results.size(), "limit", limit));
        return results;
    }

    public Map<String,Object> acknowledgeAlert(long id) {
        support.require("COORDINADOR_SCOUTING", "SCOUTING_AUDITOR");
        int changed = jdbc.update("UPDATE scouting_security_alerts SET acknowledged_at=now(),acknowledged_by=? WHERE id=? AND acknowledged_at IS NULL", support.userId(), id);
        if (changed == 0 && jdbc.queryForList("SELECT id FROM scouting_security_alerts WHERE id=?", id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa alerta de seguridad");
        }
        support.audit("SECURITY_ALERT_ACKNOWLEDGED", "SECURITY_ALERT", id, Map.of());
        return support.one("SELECT id,alert_type,user_id,window_started_at,last_observed_at,occurrence_count,acknowledged_at,acknowledged_by FROM scouting_security_alerts WHERE id=?", id);
    }

    private void enqueueAcceptedEvent(UUID id) {
        String eventId = UUID.randomUUID().toString();
        String payload = support.json().writeValueAsString(Map.of("id", eventId, "type", "ProspectoAceptado",
                "version", 1, "occurredAt", OffsetDateTime.now().toString(), "source", "semillero.scouting",
                "correlationId", id.toString(), "data", Map.of("prospectId", id.toString())));
        jdbc.update("""
                INSERT INTO scouting_outbox(event_type,aggregate_id,idempotency_key,payload)
                VALUES('ProspectoAceptado',?,?,CAST(? AS jsonb)) ON CONFLICT(idempotency_key) DO NOTHING
                """, id, "ProspectoAceptado:" + id, payload);
    }

    private boolean exists(String sql, Object... args) { return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args)); }

    private static boolean allowed(String from, String to) {
        if ("ARCHIVED".equals(to)) return !List.of("INCORPORATED", "ARCHIVED").contains(from);
        return switch (from) {
            case "DISCOVERED" -> List.of("PRESELECTED", "DISCARDED").contains(to);
            case "PRESELECTED" -> List.of("OBSERVING", "DISCOVERED", "DISCARDED").contains(to);
            case "OBSERVING" -> List.of("OBSERVED", "DISCARDED").contains(to);
            case "OBSERVED" -> List.of("OBSERVING", "EVALUATED", "DISCARDED").contains(to);
            case "EVALUATED" -> List.of("RECOMMENDED", "CONTACTED", "DISCARDED").contains(to);
            case "RECOMMENDED" -> List.of("CONTACTED", "OBSERVED", "DISCARDED").contains(to);
            case "CONTACTED" -> List.of("TRIAL", "DISCARDED").contains(to);
            case "TRIAL" -> List.of("ACCEPTED", "CONTACTED", "DISCARDED").contains(to);
            case "ACCEPTED" -> "INCORPORATED".equals(to);
            case "DISCARDED", "ARCHIVED" -> "PRESELECTED".equals(to);
            default -> false;
        };
    }

    private static void checkReason(String reason) {
        if (reason.length() < 8 || reason.length() > 1000) ScoutingProspectService.bad("El motivo debe tener entre 8 y 1000 caracteres");
    }

    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
}

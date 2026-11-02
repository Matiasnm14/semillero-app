package edu.upb.semillero.scouting;

import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
final class ScoutingSupport {
    static final Set<String> CONTENT_ROLES = Set.of("SCOUT", "COORDINADOR_SCOUTING", "RECLUTADOR", "EVALUADOR");
    static final Set<String> STATUS_ROLES = Set.of("SCOUTING_STATUS");
    private final JdbcTemplate jdbc;
    private final ScoutingCryptoService crypto;
    private final ObjectMapper json;

    ScoutingSupport(JdbcTemplate jdbc, ScoutingCryptoService crypto, ObjectMapper json) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.json = json;
    }

    JdbcTemplate jdbc() { return jdbc; }
    ScoutingCryptoService crypto() { return crypto; }
    ObjectMapper json() { return json; }

    String requestFingerprint(Map<String, ?> request) {
        return crypto.fingerprint(json.writeValueAsString(canonical(request)));
    }

    private static Object canonical(Object value) {
        if (value instanceof Map<?,?> map) {
            Map<String,Object> sorted = new TreeMap<>();
            map.forEach((key, nested) -> sorted.put(String.valueOf(key), canonical(nested)));
            return sorted;
        }
        if (value instanceof List<?> list) return list.stream().map(ScoutingSupport::canonical).toList();
        return value;
    }

    UsuarioAutenticado user() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UsuarioAutenticado user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere una sesión válida");
        }
        return user;
    }

    long userId() { return user().usuarioId(); }

    boolean hasRole(String role) {
        return user().roles().contains(role);
    }

    void require(String... acceptedRoles) {
        Set<String> accepted = Set.of(acceptedRoles);
        if (user().roles().stream().noneMatch(accepted::contains)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu rol no permite esta operación de scouting");
        }
    }

    void requireContent() { require("SCOUT", "COORDINADOR_SCOUTING", "RECLUTADOR", "EVALUADOR"); }

    Map<String, Object> prospect(UUID id, boolean includeSensitive) {
        Map<String, Object> row = one("""
                SELECT id, sensitive_payload, identity_fingerprint, duplicate_review_status, category,
                       primary_position, origin_club_id, origin_tournament_category_id, source_site,
                       stage, pending_stage, pending_reason, created_by, created_at, updated_at, version, anonymized_at
                FROM scouting_prospects WHERE id=?
                """, id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("category", row.get("category"));
        out.put("primaryPosition", row.get("primary_position"));
        out.put("originClubId", row.get("origin_club_id"));
        out.put("originTournamentCategoryId", row.get("origin_tournament_category_id"));
        out.put("sourceSite", row.get("source_site"));
        out.put("stage", row.get("stage"));
        out.put("duplicateReviewStatus", row.get("duplicate_review_status"));
        out.put("pendingStage", row.get("pending_stage"));
        out.put("pendingReason", row.get("pending_reason"));
        out.put("createdBy", row.get("created_by"));
        out.put("createdAt", row.get("created_at"));
        out.put("updatedAt", row.get("updated_at"));
        out.put("version", row.get("version"));
        out.put("anonymizedAt", row.get("anonymized_at"));
        if (includeSensitive) out.put("profile", crypto.decrypt((byte[]) row.get("sensitive_payload")));
        return out;
    }

    Map<String, Object> one(String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el recurso de scouting");
        return rows.getFirst();
    }

    boolean canAccess(UUID prospectId) {
        long actor = userId();
        if ((hasRole("COORDINADOR_SCOUTING") || hasRole("RESPONSABLE_SALVAGUARDA")) && jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM scouting_prospects p JOIN scouting_user_scopes s
                    ON s.user_id=? AND s.active AND (s.category IS NULL OR s.category=p.category)
                    AND (s.site IS NULL OR s.site=p.source_site) WHERE p.id=?)
                """, Boolean.class, actor, prospectId)) return true;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM scouting_assignments WHERE prospect_id=? AND scout_user_id=? AND active)
                OR EXISTS(SELECT 1 FROM scouting_prospects WHERE id=? AND created_by=?)
                OR EXISTS(SELECT 1 FROM scouting_observation_reports WHERE prospect_id=? AND scout_user_id=?)
                """, Boolean.class, prospectId, actor, prospectId, actor, prospectId, actor));
    }

    void requireAccess(UUID prospectId) {
        if (!canAccess(prospectId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "El prospecto no está dentro de tus asignaciones o alcance");
    }

    void requireProspectRole(UUID prospectId) {
        requireContent();
        requireAccess(prospectId);
    }

    void audit(String action, String resourceType, Object resourceId, Map<String, ?> metadata) {
        String body;
        try { body = json.writeValueAsString(metadata == null ? Map.of() : metadata); }
        catch (RuntimeException e) { body = "{}"; }
        jdbc.update("""
                INSERT INTO scouting_audit_events(actor_user_id,action,resource_type,resource_id,metadata)
                VALUES(?,?,?,?,CAST(? AS jsonb))
                """, userId(), action, resourceType, resourceId == null ? null : resourceId.toString(), body);
    }

    static String text(Map<String, ?> data, String key, boolean required) {
        Object value = data.get(key);
        String result = value == null ? null : value.toString().trim();
        if (required && (result == null || result.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el campo " + key);
        }
        return result == null || result.isBlank() ? null : result;
    }

    static UUID uuid(String value, String field) {
        try { return UUID.fromString(value); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " no es un UUID válido"); }
    }

    static String normalized(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static Map<String, Object> map(JsonNode node) {
        Map<String, Object> out = new LinkedHashMap<>();
        node.properties().forEach(entry -> out.put(entry.getKey(), entry.getValue().isNull() ? null
                : entry.getValue().isValueNode() ? entry.getValue().asString() : entry.getValue().toString()));
        return out;
    }
}

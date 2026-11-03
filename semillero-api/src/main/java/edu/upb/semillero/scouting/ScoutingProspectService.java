package edu.upb.semillero.scouting;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Service
@Transactional
public class ScoutingProspectService {
    private final ScoutingSupport support;
    private final JdbcTemplate jdbc;

    ScoutingProspectService(ScoutingSupport support) {
        this.support = support;
        this.jdbc = support.jdbc();
    }

    public Map<String, Object> createProspect(Map<String, Object> request) {
        support.require("SCOUT", "COORDINADOR_SCOUTING");
        String first = ScoutingSupport.text(request, "firstName", true);
        String last = ScoutingSupport.text(request, "lastName", true);
        String birthDate = ScoutingSupport.text(request, "birthDate", true);
        LocalDate dob = parseDate(birthDate, "birthDate");
        if (dob.isAfter(LocalDate.now()) || dob.isBefore(LocalDate.of(1900, 1, 1))) bad("birthDate fuera de rango");
        String category = ScoutingSupport.text(request, "category", true);
        if (category.length() > 80) bad("category supera 80 caracteres");
        String position = ScoutingSupport.text(request, "primaryPosition", false);
        String site = ScoutingSupport.text(request, "sourceSite", false);
        if (position != null && position.length() > 80) bad("primaryPosition supera 80 caracteres");
        if (site != null && site.length() > 120) bad("sourceSite supera 120 caracteres");
        Long originClub = positiveLong(request.get("originClubId"), "originClubId", false);
        Long originCategory = positiveLong(request.get("originTournamentCategoryId"), "originTournamentCategoryId", false);
        if (originClub != null) support.one("SELECT id FROM clubes WHERE id=?", originClub);
        if (originCategory != null) support.one("SELECT id FROM torneo_categorias WHERE id=?", originCategory);
        String canonical = ScoutingSupport.normalized(first) + "|" + ScoutingSupport.normalized(last) + "|" + dob;
        String fingerprint = support.crypto().fingerprint(canonical);
        var duplicateIds = visibleDuplicateIds(jdbc.queryForList(
                "SELECT id FROM scouting_prospects WHERE identity_fingerprint=? AND stage<>'ARCHIVED'", UUID.class, fingerprint), support::canAccess);
        String review = duplicateIds.isEmpty() ? "NOT_DUPLICATE" : "NEEDS_REVIEW";
        Map<String, Object> sensitive = new LinkedHashMap<>();
        sensitive.put("firstName", first); sensitive.put("lastName", last); sensitive.put("birthDate", dob.toString());
        for (String key : List.of("email", "phone")) {
            String value = ScoutingSupport.text(request, key, false);
            if (value != null && value.length() > 254) bad(key + " supera 254 caracteres");
            sensitive.put(key, value);
        }
        UUID id = jdbc.queryForObject("""
                INSERT INTO scouting_prospects(sensitive_payload,identity_fingerprint,duplicate_review_status,
                    category,primary_position,origin_club_id,origin_tournament_category_id,source_site,created_by)
                VALUES(?,?,?,?,?,?,?,?,?) RETURNING id
                """, UUID.class, support.crypto().encrypt(sensitive), fingerprint, review, category,
                position, originClub, originCategory, site, support.userId());
        support.audit("PROSPECT_CREATED", "PROSPECT", id, Map.of("category", category, "possibleDuplicatesInScope", duplicateIds.size()));
        Map<String, Object> response = support.prospect(id, true);
        response.put("possibleDuplicateFound", !duplicateIds.isEmpty());
        response.put("possibleDuplicateCount", duplicateIds.size());
        response.put("possibleDuplicateIds", duplicateIds.stream().map(UUID::toString).toList());
        return response;
    }

    public Map<String, Object> getProspect(UUID id) {
        support.requireProspectRole(id);
        support.audit("PROSPECT_VIEWED", "PROSPECT", id, Map.of("purpose", "SCOUTING_REVIEW"));
        return support.prospect(id, true);
    }

    public Map<String, Object> listProspects(String stage, String category, int page, int size) {
        support.requireContent();
        if (page < 0 || size < 1 || size > 100) bad("page debe ser >=0 y size entre 1 y 100");
        StringBuilder where = new StringBuilder(" WHERE p.stage<>'ARCHIVED' AND (");
        List<Object> args = new ArrayList<>();
        long actor = support.userId();
        where.append("EXISTS(SELECT 1 FROM scouting_assignments a WHERE a.prospect_id=p.id AND a.scout_user_id=? AND a.active)");
        args.add(actor);
        where.append(" OR EXISTS(SELECT 1 FROM scouting_prospects own WHERE own.id=p.id AND own.created_by=?)");
        args.add(actor);
        where.append(" OR EXISTS(SELECT 1 FROM scouting_observation_reports r WHERE r.prospect_id=p.id AND r.scout_user_id=?)");
        args.add(actor);
        if (support.hasRole("COORDINADOR_SCOUTING")) {
            where.append(" OR EXISTS(SELECT 1 FROM scouting_user_scopes s WHERE s.user_id=? AND s.active AND (s.category IS NULL OR s.category=p.category) AND (s.site IS NULL OR s.site=p.source_site))");
            args.add(actor);
        }
        where.append(')');
        if (stage != null && !stage.isBlank()) {
            if (!validStage(stage)) bad("stage no válido");
            where.append(" AND p.stage=?"); args.add(stage);
        }
        if (category != null && !category.isBlank()) { where.append(" AND p.category=?"); args.add(category); }
        Long total = jdbc.queryForObject("SELECT count(*) FROM scouting_prospects p" + where, Long.class, args.toArray());
        String sql = "SELECT p.id FROM scouting_prospects p" + where + " ORDER BY p.updated_at DESC,p.id DESC LIMIT ? OFFSET ?";
        args.add(size);
        args.add((long) page * size);
        List<UUID> ids = jdbc.queryForList(sql, UUID.class, args.toArray());
        List<Map<String, Object>> content = ids.stream().map(id -> {
            support.audit("PROSPECT_VIEWED", "PROSPECT", id, Map.of("purpose", "SCOUTING_SEARCH_RESULT"));
            return support.prospect(id, true);
        }).toList();
        long totalElements = total == null ? 0 : total;
        return Map.of("content", content, "page", page, "size", size, "totalElements", totalElements,
                "totalPages", totalElements == 0 ? 0 : (totalElements + size - 1) / size);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> duplicateCandidates(String firstName, String lastName, String birthDate) {
        support.require("SCOUT", "COORDINADOR_SCOUTING", "EVALUADOR");
        LocalDate dob = parseDate(birthDate, "birthDate");
        String fingerprint = support.crypto().fingerprint(ScoutingSupport.normalized(firstName) + "|"
                + ScoutingSupport.normalized(lastName) + "|" + dob);
        List<Map<String,Object>> rows = jdbc.queryForList("""
                SELECT id,category,stage,duplicate_review_status,created_at FROM scouting_prospects
                WHERE identity_fingerprint=? AND stage<>'ARCHIVED' ORDER BY created_at
                """, fingerprint);
        List<Map<String,Object>> visible = rows.stream().filter(row -> support.canAccess(UUID.fromString(row.get("id").toString()))).map(row -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("id", row.get("id")); out.put("category", row.get("category"));
                    out.put("stage", row.get("stage")); out.put("reviewStatus", row.get("duplicate_review_status"));
                    out.put("createdAt", row.get("created_at")); return out;
                }).toList();
        return visible;
    }

    public Map<String, Object> reviewDuplicate(UUID id, Map<String, Object> request) {
        support.require("COORDINADOR_SCOUTING"); support.requireAccess(id);
        String decision = ScoutingSupport.text(request, "decision", true);
        String reason = ScoutingSupport.text(request, "reason", true);
        UUID canonical = request.get("canonicalProspectId") == null ? null
                : ScoutingSupport.uuid(request.get("canonicalProspectId").toString(), "canonicalProspectId");
        if (!List.of("CONFIRMED_DUPLICATE", "NOT_DUPLICATE", "MERGE").contains(decision)) bad("decision no válida");
        if ("MERGE".equals(decision) || "CONFIRMED_DUPLICATE".equals(decision)) {
            if (canonical == null || canonical.equals(id)) bad("MERGE requiere otro canonicalProspectId");
            support.requireAccess(canonical);
            mergeProspects(id, canonical, reason);
            decision = "MERGED";
        } else {
            jdbc.update("UPDATE scouting_prospects SET duplicate_review_status=?,duplicate_reviewed_by=?,duplicate_reviewed_at=now(),version=version+1,updated_at=now() WHERE id=?",
                    decision, support.userId(), id);
        }
        jdbc.update("INSERT INTO scouting_duplicate_reviews(prospect_id,canonical_prospect_id,decision,reason,reviewed_by) VALUES(?,?,?,?,?)",
                id, canonical, decision, reason, support.userId());
        support.audit("DUPLICATE_REVIEWED", "PROSPECT", id, Map.of("decision", decision));
        Map<String,Object> result = new LinkedHashMap<>(); result.put("prospectId", id); result.put("decision", decision);
        result.put("canonicalProspectId", canonical); return result;
    }

    private void mergeProspects(UUID duplicate, UUID canonical, String reason) {
        Map<String,Object> source = support.one("SELECT id,stage FROM scouting_prospects WHERE id=?", duplicate);
        Map<String,Object> target = support.one("SELECT id,duplicate_review_status FROM scouting_prospects WHERE id=? AND merged_into_prospect_id IS NULL", canonical);
        if (!"NOT_DUPLICATE".equals(target.get("duplicate_review_status"))) conflict("El prospecto canónico también requiere resolver su revisión de duplicados");
        if (jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM scouting_prospects WHERE id=? AND merged_into_prospect_id IS NOT NULL)", Boolean.class, duplicate)) bad("El prospecto ya fue consolidado");
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM scouting_observation_reports a JOIN scouting_observation_reports b
                    ON b.prospect_id=? AND a.scout_user_id=b.scout_user_id AND a.idempotency_key=b.idempotency_key
                    WHERE a.prospect_id=?) OR EXISTS(SELECT 1 FROM scouting_communications a JOIN scouting_communications b
                    ON b.prospect_id=? AND a.requested_by=b.requested_by AND a.idempotency_key=b.idempotency_key
                    WHERE a.prospect_id=?)
                """, Boolean.class, canonical, duplicate, canonical, duplicate))) conflict("La consolidación tiene claves idempotentes duplicadas; resuélvelas antes de continuar");
        boolean duplicateLink = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM scouting_player_links WHERE prospect_id=?)", Boolean.class, duplicate);
        boolean canonicalLink = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM scouting_player_links WHERE prospect_id=?)", Boolean.class, canonical);
        if (duplicateLink && canonicalLink) throw new ResponseStatusException(HttpStatus.CONFLICT, "No se consolidan prospectos que ya están vinculados a jugadores distintos");
        if (duplicateLink) jdbc.update("UPDATE scouting_player_links SET prospect_id=? WHERE prospect_id=?", canonical, duplicate);
        for (String table : List.of("scouting_assignments", "scouting_observation_reports", "scouting_stage_history",
                "scouting_tutor_consents", "scouting_communications", "scouting_trials")) {
            jdbc.update("UPDATE " + table + " SET prospect_id=? WHERE prospect_id=?", canonical, duplicate);
        }
        jdbc.update("UPDATE scouting_outbox SET aggregate_id=? WHERE aggregate_id=?", canonical, duplicate);
        jdbc.update("UPDATE scouting_prospects SET stage='ARCHIVED',merged_into_prospect_id=?,duplicate_review_status='CONFIRMED_DUPLICATE',duplicate_reviewed_by=?,duplicate_reviewed_at=now(),pending_stage=NULL,pending_reason=NULL,pending_requested_by=NULL,version=version+1,updated_at=now() WHERE id=?",
                canonical, support.userId(), duplicate);
        jdbc.update("INSERT INTO scouting_stage_history(prospect_id,from_stage,to_stage,reason,changed_by) VALUES(?,?,'ARCHIVED',?,?)",
                duplicate, source.get("stage"), "Consolidación: " + reason, support.userId());
    }

    public List<Map<String, Object>> assignments(String prospectId) {
        if (prospectId != null) support.requireProspectRole(ScoutingSupport.uuid(prospectId, "prospectId"));
        else support.require("SCOUT", "COORDINADOR_SCOUTING");
        String sql = "SELECT id,prospect_id,scout_user_id,assigned_by,match_id,scheduled_at,due_at,location,criteria_set_id,form_version,reason,assigned_at,completed_at,active FROM scouting_assignments";
        List<Map<String,Object>> rows = jdbc.queryForList(sql + " ORDER BY due_at NULLS LAST,assigned_at DESC");
        return rows.stream().filter(row -> support.canAccess(UUID.fromString(row.get("prospect_id").toString()))
                        && (support.hasRole("COORDINADOR_SCOUTING") || ((Number) row.get("scout_user_id")).longValue() == support.userId()))
                .filter(row -> prospectId == null || row.get("prospect_id").toString().equals(prospectId)).toList();
    }

    public List<Map<String, Object>> assign(Map<String, Object> request) {
        support.require("COORDINADOR_SCOUTING");
        UUID prospectId = ScoutingSupport.uuid(ScoutingSupport.text(request, "prospectId", true), "prospectId");
        Map<String,Object> prospect = support.one("SELECT id,category FROM scouting_prospects WHERE id=?", prospectId);
        support.requireAccess(prospectId);
        UUID criteriaSetId = ScoutingSupport.uuid(ScoutingSupport.text(request, "criteriaSetId", true), "criteriaSetId");
        Map<String,Object> criteriaSet = support.one("SELECT id,category,version,status FROM scouting_criteria_sets WHERE id=?", criteriaSetId);
        if (!"PUBLISHED".equals(criteriaSet.get("status")) || !prospect.get("category").equals(criteriaSet.get("category"))) {
            conflict("El formulario debe estar publicado y corresponder a la categoría del prospecto");
        }
        String reason = ScoutingSupport.text(request, "reason", false);
        if (reason != null && reason.length() > 1000) bad("reason supera 1000 caracteres");
        boolean reassign = Boolean.TRUE.equals(request.get("reassign"));
        if (reassign && (reason == null || reason.length() < 8)) bad("La reasignación requiere un motivo de al menos 8 caracteres");
        Object raw = request.get("scoutUserIds");
        if (!(raw instanceof List<?>)) bad("scoutUserIds debe ser una lista");
        List<?> users = (List<?>) raw;
        if (users.isEmpty() || users.size() > 10) bad("scoutUserIds debe incluir entre 1 y 10 usuarios");
        Set<Long> uniqueUsers = new java.util.HashSet<>();
        for (Object rawUser : users) {
            long scoutId = positiveLong(rawUser, "scoutUserIds", true);
            if (!uniqueUsers.add(scoutId)) bad("scoutUserIds no debe repetir usuarios");
        }
        Long matchId = positiveLong(request.get("matchId"), "matchId", false);
        if (matchId != null) support.one("SELECT id FROM partidos WHERE id=?", matchId);
        String location = ScoutingSupport.text(request, "location", true);
        if (location.length() > 240) bad("location supera 240 caracteres");
        OffsetDateTime scheduledAt = parseTimestamp(request.get("scheduledAt"), "scheduledAt");
        if (scheduledAt == null) bad("scheduledAt es obligatorio para la asignación");
        OffsetDateTime dueAt = parseTimestamp(request.get("dueAt"), "dueAt");
        if (reassign) jdbc.update("UPDATE scouting_assignments SET active=false,completed_at=coalesce(completed_at,now()),reason=left(concat(coalesce(reason,''),' | Reasignada: ',?),1000) WHERE prospect_id=? AND active", reason, prospectId);
        List<Map<String,Object>> out = new ArrayList<>();
        for (Object rawUser : users) {
            long scoutId = positiveLong(rawUser, "scoutUserIds", true);
            support.one("SELECT id FROM users WHERE id=?", scoutId);
            if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM usuarios_roles ur JOIN roles r ON r.id=ur.roles_id WHERE ur.users_id=? AND r.codigo='SCOUT')", Boolean.class, scoutId))) conflict("Los usuarios asignados deben tener el rol SCOUT");
            UUID id = jdbc.queryForObject("""
                    INSERT INTO scouting_assignments(prospect_id,scout_user_id,assigned_by,match_id,scheduled_at,due_at,location,
                        criteria_set_id,form_version,reason)
                    VALUES(?,?,?,?,?,?,?,?,?,?) RETURNING id
                    """, UUID.class, prospectId, scoutId, support.userId(), matchId, scheduledAt, dueAt, location,
                    criteriaSetId, criteriaSet.get("version").toString(), reason);
            out.add(support.one("SELECT id,prospect_id,scout_user_id,assigned_by,match_id,scheduled_at,due_at,location,criteria_set_id,form_version,reason,assigned_at,active FROM scouting_assignments WHERE id=?", id));
        }
        support.audit("ASSIGNMENT_CREATED", "PROSPECT", prospectId, Map.of("assignees", users.size(), "reassigned", reassign));
        return out;
    }

    public List<Map<String, Object>> criteriaSets(String category) {
        support.requireContent();
        String sql = "SELECT id,category,version,title,status,created_by,created_at FROM scouting_criteria_sets";
        List<Map<String,Object>> sets = category == null ? jdbc.queryForList(sql + " ORDER BY category,version DESC")
                : jdbc.queryForList(sql + " WHERE category=? ORDER BY version DESC", category);
        for (Map<String,Object> set : sets) {
            set.put("criteria", jdbc.queryForList("SELECT id,dimension,code,label,observable_behavior,anchors,display_order FROM scouting_criteria WHERE criteria_set_id=? ORDER BY display_order,code", set.get("id")));
        }
        return sets;
    }

    public Map<String, Object> createCriteriaSet(Map<String, Object> request) {
        support.require("COORDINADOR_SCOUTING", "EVALUADOR");
        String category = ScoutingSupport.text(request, "category", true);
        String title = ScoutingSupport.text(request, "title", true);
        if (category.length() > 80 || title.length() > 160) bad("category o title exceden la longitud permitida");
        Object raw = request.get("criteria");
        if (!(raw instanceof List<?>)) bad("criteria debe ser una lista");
        List<?> criteria = (List<?>) raw;
        if (criteria.isEmpty() || criteria.size() > 80) bad("criteria debe contener entre 1 y 80 criterios");
        int version = jdbc.queryForObject("SELECT coalesce(max(version),0)+1 FROM scouting_criteria_sets WHERE category=?", Integer.class, category);
        UUID id = jdbc.queryForObject("INSERT INTO scouting_criteria_sets(category,version,title,created_by) VALUES(?,?,?,?) RETURNING id",
                UUID.class, category, version, title, support.userId());
        int order = 0;
        for (Object item : criteria) {
            if (!(item instanceof Map<?,?>)) bad("Cada criterio debe ser un objeto");
            Map<?,?> rawMap = (Map<?,?>) item;
            @SuppressWarnings("unchecked") Map<String,Object> c = (Map<String,Object>) rawMap;
            String dimension = ScoutingSupport.text(c, "dimension", true);
            String code = ScoutingSupport.text(c, "code", true);
            String label = ScoutingSupport.text(c, "label", true);
            String behavior = ScoutingSupport.text(c, "observableBehavior", true);
            if (code.length() > 80 || label.length() > 160 || behavior.length() > 1000) {
                bad("code, label u observableBehavior exceden la longitud permitida");
            }
            if (!List.of("TECHNICAL","TACTICAL","PHYSICAL","PSYCHOLOGICAL").contains(dimension)) bad("dimension no válida");
            if ("PSYCHOLOGICAL".equals(dimension) && behavior.toLowerCase().matches(".*(diagn[oó]stic|depresi[oó]n|trastorno|patolog[ií]a|ansiedad cl[ií]nica).*")) {
                bad("La evaluación psicológica solo puede describir conducta deportiva observable, sin diagnósticos");
            }
            Object anchors = c.get("anchors");
            if (!(anchors instanceof List<?>)) bad("Cada criterio requiere cinco descriptores observables para la escala 1–5");
            List<?> values = (List<?>) anchors;
            if (values.size() != 5 || values.stream().anyMatch(v -> !(v instanceof String s) || s.isBlank())) {
                bad("Cada criterio requiere cinco descriptores observables para la escala 1–5");
            }
            String json = support.json().writeValueAsString(anchors);
            jdbc.update("""
                    INSERT INTO scouting_criteria(criteria_set_id,dimension,code,label,observable_behavior,anchors,display_order)
                    VALUES(?,?,?,?,?,CAST(? AS jsonb),?)
                    """, id, dimension, code, label, behavior, json, order++);
        }
        support.audit("CRITERIA_SET_CREATED", "CRITERIA_SET", id, Map.of("category", category, "version", version));
        return criteriaSet(id);
    }

    public Map<String,Object> publishCriteria(UUID id) {
        support.require("COORDINADOR_SCOUTING", "EVALUADOR");
        Map<String,Object> set = support.one("SELECT id,category,status FROM scouting_criteria_sets WHERE id=?", id);
        if (!"DRAFT".equals(set.get("status"))) conflict("Solo se publican criterios en borrador");
        jdbc.update("UPDATE scouting_criteria_sets SET status='RETIRED' WHERE category=? AND status='PUBLISHED'", set.get("category"));
        jdbc.update("UPDATE scouting_criteria_sets SET status='PUBLISHED' WHERE id=?", id);
        support.audit("CRITERIA_SET_PUBLISHED", "CRITERIA_SET", id, Map.of());
        return criteriaSet(id);
    }

    private Map<String,Object> criteriaSet(UUID id) {
        Map<String,Object> set = support.one("SELECT id,category,version,title,status,created_by,created_at FROM scouting_criteria_sets WHERE id=?", id);
        set.put("criteria", jdbc.queryForList("SELECT id,dimension,code,label,observable_behavior,anchors,display_order FROM scouting_criteria WHERE criteria_set_id=? ORDER BY display_order,code", id));
        return set;
    }

    public Map<String,Object> createReport(Map<String,Object> request, String headerKey) {
        UUID prospectId = ScoutingSupport.uuid(ScoutingSupport.text(request, "prospectId", true), "prospectId");
        support.require("SCOUT", "EVALUADOR"); support.requireAccess(prospectId);
        String idempotency = headerKey == null || headerKey.isBlank()
                ? ScoutingSupport.text(request, "idempotencyKey", true) : headerKey.trim();
        if (idempotency.length() > 120) bad("Idempotency-Key supera 120 caracteres");
        String requestFingerprint = support.requestFingerprint(request);
        var existing = jdbc.queryForList("SELECT id,request_fingerprint FROM scouting_observation_reports WHERE scout_user_id=? AND idempotency_key=?",
                support.userId(), idempotency);
        if (!existing.isEmpty()) {
            if (!requestFingerprint.equals(existing.getFirst().get("request_fingerprint"))) conflict("Idempotency-Key ya fue usado con datos diferentes");
            return report(UUID.fromString(existing.getFirst().get("id").toString()));
        }
        UUID criteriaSet = ScoutingSupport.uuid(ScoutingSupport.text(request, "criteriaSetId", true), "criteriaSetId");
        Map<String,Object> criteria = support.one("SELECT category,status FROM scouting_criteria_sets WHERE id=?", criteriaSet);
        Map<String,Object> prospect = support.one("SELECT category FROM scouting_prospects WHERE id=?", prospectId);
        if ("DRAFT".equals(criteria.get("status")) || !criteria.get("category").equals(prospect.get("category"))) conflict("El conjunto de criterios no corresponde a la categoría o aún no fue publicado");
        UUID assignmentUuid = ScoutingSupport.uuid(ScoutingSupport.text(request, "assignmentId", true), "assignmentId");
        Map<String,Object> assignment = support.one("SELECT prospect_id,scout_user_id,criteria_set_id FROM scouting_assignments WHERE id=? AND active", assignmentUuid);
        if (!prospectId.toString().equals(assignment.get("prospect_id").toString())
                || ((Number) assignment.get("scout_user_id")).longValue() != support.userId()) forbidden();
        requireAssignedCriteriaSet(assignment.get("criteria_set_id"), criteriaSet);
        String observed = ScoutingSupport.text(request, "observedAt", true);
        OffsetDateTime observedAt;
        try { observedAt = OffsetDateTime.parse(observed); }
        catch (Exception e) { bad("observedAt debe incluir zona horaria ISO-8601"); return null; }
        String confidence = ScoutingSupport.text(request, "confidence", true);
        if (!List.of("LOW","MEDIUM","HIGH").contains(confidence)) bad("confidence debe ser LOW, MEDIUM o HIGH");
        Integer performance = rating(request.get("currentPerformance"), "currentPerformance");
        Integer potential = rating(request.get("potential"), "potential");
        Integer minutes = request.get("minutesPlayed") == null ? null : integerRange(request.get("minutesPlayed"), "minutesPlayed", 0, 180);
        List<Map<String,Object>> criteriaRows = jdbc.queryForList("SELECT id FROM scouting_criteria WHERE criteria_set_id=?", criteriaSet);
        Object rawScores = request.get("scores");
        if (!(rawScores instanceof List<?>)) bad("scores debe ser una lista");
        List<?> scores = (List<?>) rawScores;
        if (scores.size() != criteriaRows.size()) bad("Debe responder cada criterio de la versión asignada, incluidos los no observados");
        Map<String,Object> scoreById = new LinkedHashMap<>();
        for (Object raw : scores) {
            if (!(raw instanceof Map<?,?>)) bad("Cada score debe ser un objeto");
            Map<?,?> rawMap = (Map<?,?>) raw;
            @SuppressWarnings("unchecked") Map<String,Object> score = (Map<String,Object>) rawMap;
            UUID criterion = ScoutingSupport.uuid(ScoutingSupport.text(score, "criterionId", true), "criterionId");
            if (scoreById.put(criterion.toString(), score) != null) bad("Criterio repetido");
        }
        for (Map<String,Object> criterion : criteriaRows) if (!scoreById.containsKey(criterion.get("id").toString())) bad("Falta un criterio de la versión");
        String narrative = ScoutingSupport.text(request, "narrative", false);
        Map<String,Object> narrativePayload = Map.of("text", narrative == null ? "" : narrative);
        String opponent = ScoutingSupport.text(request, "opponent", false);
        String position = ScoutingSupport.text(request, "playedPosition", false);
        if (opponent != null && opponent.length() > 160) bad("opponent supera 160 caracteres");
        if (position != null && position.length() > 80) bad("playedPosition supera 80 caracteres");
        Long matchId = positiveLong(request.get("matchId"), "matchId", false);
        if (matchId != null) support.one("SELECT id FROM partidos WHERE id=?", matchId);
        if (matchId != null) {
            Object assignedMatch = support.one("SELECT match_id FROM scouting_assignments WHERE id=?", assignmentUuid).get("match_id");
            if (assignedMatch != null && ((Number) assignedMatch).longValue() != matchId) conflict("El partido no coincide con la asignación");
        }
        UUID id = jdbc.queryForList("""
                INSERT INTO scouting_observation_reports(prospect_id,assignment_id,scout_user_id,match_id,observed_at,
                    opponent,played_position,minutes_played,current_performance,potential,confidence,criteria_set_id,
                    narrative_cipher,idempotency_key,request_fingerprint)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(scout_user_id,idempotency_key) DO NOTHING RETURNING id
                """, UUID.class, prospectId, assignmentUuid, support.userId(), matchId, observedAt, opponent, position,
                minutes, performance, potential, confidence, criteriaSet, support.crypto().encrypt(narrativePayload),
                idempotency, requestFingerprint).stream().findFirst().orElse(null);
        if (id == null) {
            Map<String,Object> replay = support.one("SELECT id,request_fingerprint FROM scouting_observation_reports WHERE scout_user_id=? AND idempotency_key=?", support.userId(), idempotency);
            if (!requestFingerprint.equals(replay.get("request_fingerprint"))) conflict("Idempotency-Key ya fue usado con datos diferentes");
            return report(UUID.fromString(replay.get("id").toString()));
        }
        for (var entry : scoreById.entrySet()) {
            Map<String,Object> score = (Map<String,Object>) entry.getValue();
            Object observedValue = score.get("observed");
            if (!(observedValue instanceof Boolean)) bad("observed debe ser booleano");
            boolean wasObserved = (Boolean) observedValue;
            Integer value = rating(score.get("score"), "score");
            if (wasObserved && value == null || !wasObserved && value != null) bad("Un criterio observado requiere score 1–5 y uno no observado debe quedar sin score");
            String note = ScoutingSupport.text(score, "note", false);
            byte[] noteCipher = note == null ? null : support.crypto().encrypt(Map.of("note", note));
            jdbc.update("INSERT INTO scouting_observation_scores(report_id,criterion_id,observed,score,note_cipher) VALUES(?,?,?,?,?)",
                    id, UUID.fromString(entry.getKey()), wasObserved, value, noteCipher);
        }
        saveEvidence(id, request.get("evidence"));
        if (assignmentUuid != null) jdbc.update("UPDATE scouting_assignments SET completed_at=coalesce(completed_at,now()) WHERE id=?", assignmentUuid);
        support.audit("OBSERVATION_CREATED", "OBSERVATION", id, Map.of("prospectId", prospectId.toString(), "criteriaSetId", criteriaSet.toString()));
        return report(id);
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> assignmentsMine() {
        support.require("SCOUT", "COORDINADOR_SCOUTING");
        long user = support.userId();
        return jdbc.queryForList("""
                SELECT id,prospect_id,scout_user_id,assigned_by,match_id,scheduled_at,due_at,location,criteria_set_id,form_version,reason,assigned_at,completed_at,active
                FROM scouting_assignments WHERE scout_user_id=? AND active ORDER BY due_at NULLS LAST,assigned_at
                """, user).stream().map(row -> {
                    row.put("prospect", support.prospect(UUID.fromString(row.get("prospect_id").toString()), false)); return row;
                }).toList();
    }

    public Map<String,Object> history(UUID id) {
        support.requireProspectRole(id);
        support.audit("PROSPECT_HISTORY_VIEWED", "PROSPECT", id, Map.of("purpose", "SCOUTING_REVIEW"));
        List<Map<String,Object>> reports = jdbc.queryForList("""
                SELECT id,assignment_id,scout_user_id,match_id,observed_at,opponent,played_position,minutes_played,
                       current_performance,potential,confidence,criteria_set_id,narrative_cipher
                FROM scouting_observation_reports WHERE prospect_id=? ORDER BY observed_at,id
                """, id).stream().map(row -> {
                    Map<String,Object> out = new LinkedHashMap<>(row);
                    byte[] cipher = (byte[]) out.remove("narrative_cipher");
                    out.put("narrative", support.crypto().decrypt(cipher));
                    out.put("evidence", jdbc.queryForList("SELECT id,evidence_type,uri,description,created_at FROM scouting_report_evidence WHERE report_id=? ORDER BY created_at,id", row.get("id")));
                    out.put("scores", jdbc.queryForList("""
                            SELECT s.criterion_id,s.observed,s.score,c.dimension,c.code,c.label,c.observable_behavior,c.anchors,s.note_cipher
                            FROM scouting_observation_scores s JOIN scouting_criteria c ON c.id=s.criterion_id
                            WHERE s.report_id=? ORDER BY c.display_order,c.code
                            """, row.get("id")).stream().map(score -> {
                                Map<String,Object> result = new LinkedHashMap<>(score);
                                byte[] noteCipher = (byte[]) result.remove("note_cipher");
                                if (noteCipher != null) result.put("note", support.crypto().decrypt(noteCipher));
                                return result;
                            }).toList());
                    return out;
                }).toList();
        List<Map<String,Object>> stages = jdbc.queryForList("SELECT id,from_stage,to_stage,reason,changed_by,reviewed_by,evidence_reference,changed_at FROM scouting_stage_history WHERE prospect_id=? ORDER BY changed_at,id", id);
        List<Map<String,Object>> communications = jdbc.queryForList("""
                SELECT c.id,c.channel,c.status,c.requested_at,c.sent_at,c.attempts,t.code AS template_code
                FROM scouting_communications c JOIN scouting_message_templates t ON t.id=c.template_id
                WHERE c.prospect_id=? ORDER BY c.requested_at,c.id
                """, id);
        List<Map<String,Object>> timeline = new ArrayList<>();
        for (Map<String,Object> report : reports) {
            timeline.add(timelineEvent("OBSERVATION", report.get("id"), report.get("observed_at"),
                    "assignmentId", report.get("assignment_id"), "scoutUserId", report.get("scout_user_id"),
                    "opponent", report.get("opponent"), "playedPosition", report.get("played_position"),
                    "minutesPlayed", report.get("minutes_played"), "currentPerformance", report.get("current_performance"),
                    "potential", report.get("potential"), "confidence", report.get("confidence")));
        }
        for (Map<String,Object> stage : stages) {
            timeline.add(timelineEvent("STAGE_DECISION", stage.get("id"), stage.get("changed_at"),
                    "fromStage", stage.get("from_stage"), "toStage", stage.get("to_stage"),
                    "reason", stage.get("reason"), "changedBy", stage.get("changed_by"), "reviewedBy", stage.get("reviewed_by")));
        }
        for (Map<String,Object> communication : communications) {
            timeline.add(timelineEvent("CONTACT", communication.get("id"), communication.get("requested_at"),
                    "communicationId", communication.get("id"), "channel", communication.get("channel"),
                    "status", communication.get("status"), "templateCode", communication.get("template_code"),
                    "sentAt", communication.get("sent_at"), "attempts", communication.get("attempts")));
        }
        timeline.sort(Comparator.comparing(item -> Instant.parse(item.get("timestamp").toString())));
        Long distinct = jdbc.queryForObject("SELECT count(DISTINCT scout_user_id) FROM scouting_observation_reports WHERE prospect_id=?", Long.class, id);
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("prospectId", id);
        response.put("observations", reports);
        response.put("stageHistory", stages);
        response.put("communications", communications);
        response.put("timeline", timeline);
        response.put("observationCount", reports.size());
        response.put("distinctScouts", distinct == null ? 0 : distinct);
        return response;
    }

    public Map<String,Object> context(UUID id) {
        support.requireProspectRole(id);
        support.audit("PROSPECT_CONTEXT_VIEWED", "PROSPECT", id, Map.of("purpose", "SCOUTING_REVIEW"));
        Map<String,Object> prospect = support.one("SELECT origin_club_id,origin_tournament_category_id FROM scouting_prospects WHERE id=?", id);
        Map<String,Object> result = new LinkedHashMap<>();
        Object sourceClub = prospect.get("origin_club_id");
        result.put("originClub", sourceClub == null ? null : support.one("""
                SELECT c.id,c.nombre_club,ci.nombre_ciudad,p.nombre_pais FROM clubes c
                LEFT JOIN ciudad ci ON ci.id=c.ciudad_id LEFT JOIN pais p ON p.id=ci.pais_id WHERE c.id=?
                """, sourceClub));
        Object categoryId = prospect.get("origin_tournament_category_id");
        if (categoryId == null) {
            result.put("tournamentCategory", null); result.put("matches", List.of());
        } else {
            result.put("tournamentCategory", support.one("""
                    SELECT tc.id,tc.nombre,tc.edad_minima,tc.edad_maxima,t.id AS torneo_id,t.nombre AS torneo_nombre,
                           t.temporada,t.estado,t.fecha_inicio,t.fecha_fin
                    FROM torneo_categorias tc JOIN torneos t ON t.id=tc.torneos_id WHERE tc.id=?
                    """, categoryId));
            result.put("matches", jdbc.queryForList("""
                    SELECT p.id,p.fecha_inicio,p.estado,p.jornada,p.fase,p.goles_local,p.goles_visitante,
                           p.club_local_id,cl.nombre_club AS club_local,p.club_visitante_id,cv.nombre_club AS club_visitante
                    FROM partidos p JOIN clubes cl ON cl.id=p.club_local_id JOIN clubes cv ON cv.id=p.club_visitante_id
                    WHERE p.torneo_categorias_id=? ORDER BY p.fecha_inicio DESC LIMIT 30
                    """, categoryId));
        }
        var linked = jdbc.queryForList("SELECT player_id FROM scouting_player_links WHERE prospect_id=?", id);
        if (linked.isEmpty()) result.put("playerPerformanceMetrics", List.of());
        else result.put("playerPerformanceMetrics", jdbc.queryForList("""
                SELECT m.metric_id,m.match_id,p.fecha_inicio,m.total_distance,m.avg_speed,m.max_speed,
                       (m.period_end-m.period_start)/60 AS minutes,m.version,m.created_at
                FROM player_match_metrics m JOIN partidos p ON p.id=m.match_id
                WHERE m.player_id=? AND m.is_current ORDER BY p.fecha_inicio DESC LIMIT 20
                """, linked.getFirst().get("player_id")));
        return result;
    }

    private static Map<String,Object> timelineEvent(String type, Object id, Object timestamp, Object... values) {
        Map<String,Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("id", id == null ? null : id.toString());
        Instant instant = timestamp instanceof java.sql.Timestamp sqlTimestamp
                ? sqlTimestamp.toInstant()
                : timestamp instanceof OffsetDateTime offsetDateTime ? offsetDateTime.toInstant()
                : timestamp instanceof Instant value ? value
                : OffsetDateTime.parse(timestamp.toString()).toInstant();
        event.put("timestamp", instant.toString());
        for (int index = 0; index < values.length; index += 2) event.put(values[index].toString(), values[index + 1]);
        return event;
    }

    public Map<String,Object> createExport(Map<String,Object> request) {
        support.require("COORDINADOR_SCOUTING", "RECLUTADOR");
        UUID prospectId = ScoutingSupport.uuid(ScoutingSupport.text(request, "prospectId", true), "prospectId");
        support.requireAccess(prospectId);
        String reason = ScoutingSupport.text(request, "reason", true);
        if (reason.length() < 8 || reason.length() > 500) bad("reason debe tener entre 8 y 500 caracteres");
        Object rawFields = request.get("fields");
        if (!(rawFields instanceof List<?>)) bad("fields debe incluir al menos una sección exportable");
        List<String> fields = exportFields(rawFields);
        int minutes = request.get("expiresInMinutes") == null ? 30
                : integerRange(request.get("expiresInMinutes"), "expiresInMinutes", 1, 60);
        UUID exportId = UUID.randomUUID();
        OffsetDateTime generatedAt = OffsetDateTime.now();
        Map<String,Object> payload = new LinkedHashMap<>();
        if (fields.contains("profile")) {
            support.audit("PROSPECT_VIEWED", "PROSPECT", prospectId, Map.of("purpose", "AUTHORIZED_EXPORT"));
            payload.put("prospect", support.prospect(prospectId, true));
        }
        if (fields.contains("history")) payload.put("history", history(prospectId));
        if (fields.contains("tournamentContext")) payload.put("tournamentContext", context(prospectId));
        payload.put("generatedAt", generatedAt.toString());
        payload.put("watermark", Map.of("exportId", exportId.toString(), "requestedBy", support.userId(),
                "generatedAt", generatedAt.toString()));
        String serializedFields = support.json().writeValueAsString(fields);
        jdbc.update("""
                INSERT INTO scouting_exports(id,prospect_id,requested_by,payload_cipher,expires_at,reason,selected_fields)
                VALUES(?,?,?,?,now()+(? * interval '1 minute'),?,CAST(? AS jsonb))
                """, exportId, prospectId, support.userId(), support.crypto().encrypt(payload), minutes,
                reason, serializedFields);
        support.audit("EXPORT_CREATED", "EXPORT", exportId, Map.of("prospectId", prospectId.toString(),
                "selectedFields", fields, "expiresInMinutes", minutes));
        Map<String,Object> result = support.one("SELECT id,prospect_id,expires_at,reason FROM scouting_exports WHERE id=?", exportId);
        result.put("selected_fields", fields);
        return result;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String,Object> downloadExport(UUID exportId) {
        Map<String,Object> row = support.one("SELECT id,prospect_id,requested_by,payload_cipher,expires_at,reason,selected_fields::text AS selected_fields FROM scouting_exports WHERE id=?", exportId);
        if (((Number) row.get("requested_by")).longValue() != support.userId()) ScoutingProspectService.forbidden();
        support.requireAccess(UUID.fromString(row.get("prospect_id").toString()));
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT expires_at<=now() FROM scouting_exports WHERE id=?", Boolean.class, exportId))) {
            jdbc.update("DELETE FROM scouting_exports WHERE id=?", exportId);
            throw new ResponseStatusException(HttpStatus.GONE, "La exportación temporal venció");
        }
        jdbc.update("UPDATE scouting_exports SET downloaded_at=now() WHERE id=?", exportId);
        List<String> selectedFields = parseSelectedFields(row.get("selected_fields").toString());
        support.audit("EXPORT_DOWNLOADED", "EXPORT", exportId, Map.of("selectedFields", selectedFields));
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("exportId", exportId);
        response.put("reason", row.get("reason"));
        response.put("selectedFields", selectedFields);
        response.put("payload", support.crypto().decrypt((byte[]) row.get("payload_cipher")));
        return response;
    }

    public Map<String,Object> anonymize(UUID id, String reason) {
        support.require("RESPONSABLE_SALVAGUARDA", "COORDINADOR_SCOUTING"); support.requireAccess(id);
        if (reason == null || reason.trim().length() < 8 || reason.length() > 1000) bad("reason debe tener entre 8 y 1000 caracteres");
        anonymizeRecord(id, reason, support.userId());
        return Map.of("prospectId", id, "anonymized", true);
    }

    public void anonymizeExpired(int retentionDays) {
        if (retentionDays < 1) return;
        List<UUID> ids = jdbc.queryForList("""
                SELECT id FROM scouting_prospects p WHERE p.stage='ARCHIVED' AND p.anonymized_at IS NULL
                    AND p.updated_at < now()-(? * interval '1 day')
                    AND NOT EXISTS(SELECT 1 FROM scouting_assignments a WHERE a.prospect_id=p.id AND a.active)
                    AND NOT EXISTS(SELECT 1 FROM scouting_trials t WHERE t.prospect_id=p.id AND t.status='SCHEDULED')
                    AND NOT EXISTS(SELECT 1 FROM scouting_communications c WHERE c.prospect_id=p.id AND c.status='PENDING_CONFIGURATION')
                ORDER BY p.updated_at LIMIT 100
                """, UUID.class, retentionDays);
        ids.forEach(id -> anonymizeRecord(id, "Retención automática", null));
    }

    private void anonymizeRecord(UUID id, String reason, Long actorId) {
        support.one("SELECT id FROM scouting_prospects WHERE id=? AND stage='ARCHIVED' AND anonymized_at IS NULL", id);
        Map<String,Object> redacted = Map.of("anonymized", true);
        jdbc.update("""
                UPDATE scouting_prospects SET sensitive_payload=?,identity_fingerprint=?,anonymized_at=now(),
                    origin_club_id=NULL,origin_tournament_category_id=NULL,source_site=NULL,primary_position=NULL,
                    duplicate_review_status='NOT_DUPLICATE',version=version+1,updated_at=now() WHERE id=?
                """, support.crypto().encrypt(redacted), support.crypto().fingerprint("anonymized:" + id + ":" + UUID.randomUUID()), id);
        jdbc.update("UPDATE scouting_observation_reports SET narrative_cipher=NULL WHERE prospect_id=?", id);
        jdbc.update("UPDATE scouting_assignments SET reason='Motivo retenido por política de privacidad' WHERE prospect_id=? AND reason IS NOT NULL", id);
        jdbc.update("UPDATE scouting_observation_scores s SET note_cipher=NULL FROM scouting_observation_reports r WHERE s.report_id=r.id AND r.prospect_id=?", id);
        jdbc.update("UPDATE scouting_report_evidence e SET uri='redacted:'||e.id::text,description=NULL FROM scouting_observation_reports r WHERE e.report_id=r.id AND r.prospect_id=?", id);
        jdbc.update("UPDATE scouting_tutor_consents SET tutor_payload_cipher=?,evidence_reference=NULL,revoked_at=coalesce(revoked_at,now()) WHERE prospect_id=?",
                support.crypto().encrypt(redacted), id);
        jdbc.update("UPDATE scouting_communications SET recipient_cipher=?,content_cipher=?,status=CASE WHEN status='PENDING_CONFIGURATION' THEN 'CANCELLED' ELSE status END,last_error=CASE WHEN status='PENDING_CONFIGURATION' THEN 'Prospecto anonimizado' ELSE last_error END WHERE prospect_id=?",
                support.crypto().encrypt(redacted), support.crypto().encrypt(redacted), id);
        jdbc.update("UPDATE scouting_stage_history SET reason='Motivo retenido por política de privacidad',evidence_reference=NULL WHERE prospect_id=?", id);
        jdbc.update("UPDATE scouting_duplicate_reviews SET reason='Motivo retenido por política de privacidad' WHERE prospect_id=? OR canonical_prospect_id=?", id, id);
        jdbc.update("UPDATE scouting_trials SET outcome_reason='Motivo retenido por política de privacidad' WHERE prospect_id=? AND outcome_reason IS NOT NULL", id);
        jdbc.update("DELETE FROM scouting_exports WHERE prospect_id=?", id);
        jdbc.update("INSERT INTO scouting_audit_events(actor_user_id,action,resource_type,resource_id,metadata) VALUES(?, 'PROSPECT_ANONYMIZED','PROSPECT',?,CAST(? AS jsonb))",
                actorId, id.toString(), support.json().writeValueAsString(Map.of("policy", "RETENTION_ANONYMIZATION")));
    }

    private Map<String,Object> report(UUID id) {
        Map<String,Object> row = support.one("""
                SELECT id,prospect_id,assignment_id,scout_user_id,match_id,observed_at,opponent,played_position,
                       minutes_played,current_performance,potential,confidence,criteria_set_id,created_at
                FROM scouting_observation_reports WHERE id=?
                """, id);
        row.put("evidence", jdbc.queryForList("SELECT id,evidence_type,uri,description,created_at FROM scouting_report_evidence WHERE report_id=? ORDER BY created_at,id", id));
        return row;
    }

    private void saveEvidence(UUID reportId, Object rawEvidence) {
        if (rawEvidence == null) return;
        if (!(rawEvidence instanceof List<?>)) bad("evidence debe ser una lista");
        List<?> evidence = (List<?>) rawEvidence;
        if (evidence.size() > 10) bad("Un reporte admite como máximo 10 referencias de evidencia");
        for (Object raw : evidence) {
            if (!(raw instanceof Map<?,?>)) bad("Cada evidencia debe ser un objeto");
            Map<?,?> item = (Map<?,?>) raw;
            String type = item.get("type") == null ? null : item.get("type").toString();
            String uriText = item.get("uri") == null ? null : item.get("uri").toString().trim();
            String description = item.get("description") == null ? null : item.get("description").toString().trim();
            if (type == null || !List.of("VIDEO", "PHOTO", "DOCUMENT").contains(type)) bad("type de evidencia debe ser VIDEO, PHOTO o DOCUMENT");
            if (uriText == null || uriText.length() > 1000) bad("uri de evidencia es obligatoria y no supera 1000 caracteres");
            try {
                java.net.URI uri = java.net.URI.create(uriText);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                        || uri.getQuery() != null || uri.getFragment() != null) bad("La evidencia debe usar HTTPS y no incluir credenciales, query ni fragmentos");
            } catch (IllegalArgumentException e) { bad("uri de evidencia no es una URL válida"); }
            if (description != null && description.length() > 500) bad("description supera 500 caracteres");
            jdbc.update("INSERT INTO scouting_report_evidence(report_id,evidence_type,uri,description) VALUES(?,?,?,?)",
                    reportId, type, uriText, description);
        }
    }

    private static Integer rating(Object raw, String field) {
        if (raw == null) return null;
        try {
            int value = raw instanceof Number number ? number.intValue() : Integer.parseInt(raw.toString());
            if (value < 1 || value > 5) bad(field + " debe estar entre 1 y 5 o ser nulo");
            return value;
        } catch (NumberFormatException e) { bad(field + " debe ser un entero"); return null; }
    }

    static Long positiveLong(Object raw, String field, boolean required) {
        if (raw == null) { if (required) bad(field + " es obligatorio"); return null; }
        try { long value = raw instanceof Number number ? number.longValue() : Long.parseLong(raw.toString());
            if (value <= 0) bad(field + " debe ser positivo"); return value;
        } catch (NumberFormatException e) { bad(field + " debe ser un entero positivo"); return null; }
    }

    static Integer integerRange(Object raw, String field, int min, int max) {
        try {
            int value = raw instanceof Number number ? number.intValue() : Integer.parseInt(raw.toString());
            if (value < min || value > max) bad(field + " debe estar entre " + min + " y " + max);
            return value;
        } catch (NumberFormatException e) { bad(field + " debe ser un entero"); return null; }
    }

    static OffsetDateTime parseTimestamp(Object raw, String field) {
        if (raw == null) return null;
        try { return OffsetDateTime.parse(raw.toString()); }
        catch (Exception e) { bad(field + " debe ser ISO-8601 con zona horaria"); return null; }
    }

    static LocalDate parseDate(String value, String field) {
        try { return LocalDate.parse(value); }
        catch (Exception e) { bad(field + " debe usar YYYY-MM-DD"); return null; }
    }

    static List<UUID> visibleDuplicateIds(List<UUID> candidates, Predicate<UUID> canAccess) {
        return candidates.stream().filter(canAccess).toList();
    }

    static void requireAssignedCriteriaSet(Object assignedCriteriaSetId, UUID requestedCriteriaSetId) {
        if (assignedCriteriaSetId == null || !requestedCriteriaSetId.toString().equals(assignedCriteriaSetId.toString())) {
            conflict("El informe debe usar el formulario versionado de la asignación");
        }
    }

    static List<String> exportFields(Object rawFields) {
        if (!(rawFields instanceof List<?>)) bad("fields debe incluir al menos una sección exportable");
        List<String> fields = new ArrayList<>();
        Set<String> allowedFields = Set.of("profile", "history", "tournamentContext");
        for (Object rawField : (List<?>) rawFields) {
            if (!(rawField instanceof String field) || !allowedFields.contains(field)) bad("fields solo admite profile, history y tournamentContext");
            if (fields.contains(rawField)) bad("fields no debe repetir secciones");
            fields.add((String) rawField);
        }
        if (fields.isEmpty()) bad("fields debe incluir al menos una sección exportable");
        return List.copyOf(fields);
    }

    private List<String> parseSelectedFields(String serialized) {
        JsonNode values = support.json().readTree(serialized);
        if (values == null || !values.isArray()) throw new IllegalStateException("selected_fields de exportación debe ser un array JSON");
        List<String> fields = new ArrayList<>();
        values.forEach(value -> fields.add(value.asString()));
        return List.copyOf(fields);
    }

    static boolean validStage(String stage) { return List.of("DISCOVERED","PRESELECTED","OBSERVING","OBSERVED","EVALUATED","RECOMMENDED","CONTACTED","TRIAL","ACCEPTED","INCORPORATED","DISCARDED","ARCHIVED").contains(stage); }
    static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    static void forbidden() { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La operación no está dentro de tu asignación"); }
}

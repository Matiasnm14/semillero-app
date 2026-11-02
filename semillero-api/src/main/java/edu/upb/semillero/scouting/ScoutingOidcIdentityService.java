package edu.upb.semillero.scouting;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ScoutingOidcIdentityService {
    private final ScoutingSupport support;
    private final JdbcTemplate jdbc;
    private final String issuer;

    ScoutingOidcIdentityService(ScoutingSupport support, @Value("${semillero.scouting.oidc.issuer-uri:}") String issuer) {
        this.support = support; this.jdbc = support.jdbc(); this.issuer = issuer;
    }

    public Map<String,Object> create(Map<String,Object> request) {
        support.require("ADMIN");
        if (issuer == null || issuer.isBlank()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Configura SCOUTING_OIDC_ISSUER_URI antes de registrar identidades");
        String subject = ScoutingSupport.text(request, "subject", true);
        if (subject.length() > 255) ScoutingProspectService.bad("subject supera 255 caracteres");
        long userId = ScoutingProspectService.positiveLong(request.get("userId"), "userId", true);
        support.one("SELECT id FROM users WHERE id=?", userId);
        if (!jdbc.queryForList("SELECT id FROM scouting_oidc_identities WHERE issuer=? AND (subject=? OR users_id=?)", issuer, subject, userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La identidad OIDC o la cuenta ya está vinculada para este issuer");
        }
        Long id = jdbc.queryForObject("INSERT INTO scouting_oidc_identities(issuer,subject,users_id,created_by) VALUES(?,?,?,?) RETURNING id",
                Long.class, issuer, subject, userId, support.userId());
        support.audit("OIDC_IDENTITY_CREATED", "OIDC_IDENTITY", id, Map.of("userId", userId));
        return identity(id);
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> list() {
        support.require("ADMIN");
        return jdbc.queryForList("SELECT id,issuer,subject,users_id,created_by,created_at,active FROM scouting_oidc_identities ORDER BY id DESC");
    }

    public Map<String,Object> deactivate(long id) {
        support.require("ADMIN");
        int changed = jdbc.update("UPDATE scouting_oidc_identities SET active=false WHERE id=? AND active", id);
        if (changed == 0 && jdbc.queryForList("SELECT id FROM scouting_oidc_identities WHERE id=?", id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa identidad OIDC");
        }
        support.audit("OIDC_IDENTITY_DEACTIVATED", "OIDC_IDENTITY", id, Map.of());
        return identity(id);
    }

    private Map<String,Object> identity(long id) {
        return support.one("SELECT id,issuer,subject,users_id,created_by,created_at,active FROM scouting_oidc_identities WHERE id=?", id);
    }
}

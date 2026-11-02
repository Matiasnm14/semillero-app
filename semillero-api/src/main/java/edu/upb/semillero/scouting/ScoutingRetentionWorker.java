package edu.upb.semillero.scouting;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "scouting.workers.enabled", havingValue = "true", matchIfMissing = true)
public class ScoutingRetentionWorker {
    private final JdbcTemplate jdbc;
    private final ScoutingProspectService prospects;
    private final int retentionDays;

    public ScoutingRetentionWorker(JdbcTemplate jdbc, ScoutingProspectService prospects,
            @Value("${scouting.retention.days:365}") int retentionDays) {
        this.jdbc = jdbc; this.prospects = prospects; this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${scouting.retention.cron:0 0 4 * * *}")
    public void enforceRetention() {
        if (retentionDays < 1) return;
        prospects.anonymizeExpired(retentionDays);
        jdbc.update("DELETE FROM scouting_exports WHERE expires_at<=now()");
    }
}

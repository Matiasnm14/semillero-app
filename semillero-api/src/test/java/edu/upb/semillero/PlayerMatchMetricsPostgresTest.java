package edu.upb.semillero;

import edu.upb.semillero.repository.repos.PlayerMatchMetricsRepository;
import edu.upb.semillero.service.PlayerMatchMetricsIntegrityService;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.assertj.core.api.Assertions.*;

/** Destructive only within the explicitly named disposable local database. */
@EnabledIfEnvironmentVariable(named = "METRICS_PG_TEST_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/metrics_hu12_test")
class PlayerMatchMetricsPostgresTest {
    @Test
    void migratesBothDevAndExistingSchemasEnforcesMembershipAndPreservesMetricsAfterRestart() throws Exception {
        try (var context = start("create")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            // Dev already created the model: the migration must still add the composite FK.
            migrate(context, "003_player_match_metrics.sql");
            assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname='fk_pmm_participation'", Integer.class))
                    .isEqualTo(1);
            // Exercise the complete SQL chain against existing catalog tables, without JPA creating these tables.
            jdbc.execute("drop table player_match_metrics");
            jdbc.execute("drop table analysis_jobs");
            jdbc.execute("drop table videos_partido");
            migrate(context, "001_videos_partido.sql");
            migrate(context, "002_analysis_jobs.sql");
            migrate(context, "003_player_match_metrics.sql");
        }
        long metricId;
        Instant createdAt;
        try (var context = start("validate")) {
            var fixture = MetricsFixtures.create(context);
            context.getBean(PlayerMatchMetricsIntegrityService.class).validate(
                    fixture.analysis().getId(), fixture.partido().getId(), fixture.jugador().getId());
            var repository = context.getBean(PlayerMatchMetricsRepository.class);
            var saved = repository.saveAndFlush(MetricsFixtures.metric(fixture));
            metricId = saved.getId();
            createdAt = saved.getCreatedAt();
            var jdbc = context.getBean(JdbcTemplate.class);
            long analysis = fixture.analysis().getId(), match = fixture.partido().getId(), player = fixture.jugador().getId();
            assertThatThrownBy(() -> insert(jdbc, Long.MAX_VALUE, match, player))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insert(jdbc, analysis, Long.MAX_VALUE, player))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insert(jdbc, analysis, match, Long.MAX_VALUE))
                    .isInstanceOf(DataIntegrityViolationException.class);
            // All three scalar references exist, but this player is not in this match.
            assertThatThrownBy(() -> insert(jdbc, analysis, match, fixture.otroJugador().getId()))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_pmm_participation");
            assertThatThrownBy(() -> jdbc.update("delete from participaciones_partido where id=?", fixture.participacion().getId()))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_pmm_participation");
            assertThatThrownBy(() -> jdbc.update("update participaciones_partido set jugadores_id=? where id=?",
                    fixture.otroJugador().getId(), fixture.participacion().getId()))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_pmm_participation");
            assertThatThrownBy(() -> jdbc.update("update player_match_metrics set total_distance='NaN'::numeric where metric_id=?", metricId))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update player_match_metrics set avg_speed=max_speed+1 where metric_id=?", metricId))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update player_match_metrics set period_end=period_start where metric_id=?", metricId))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update player_match_metrics set version=0 where metric_id=?", metricId))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into player_match_metrics(analysis_id, match_id, player_id,
                    total_distance, avg_speed, max_speed, period_start, period_end, version, is_current, created_at)
                    select analysis_id, match_id, player_id, total_distance, avg_speed, max_speed,
                    period_start, period_end, 99, false, created_at from player_match_metrics where metric_id=?
                    """, metricId)).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_pmm_analysis_player_period");
            assertThat(repository.count()).isEqualTo(1);
        }
        try (var context = start("validate")) {
            var saved = context.getBean(PlayerMatchMetricsRepository.class).findById(metricId).orElseThrow();
            assertThat(saved.getTotalDistance()).isEqualByComparingTo("1234.567890");
            assertThat(saved.getAvgSpeed()).isEqualByComparingTo("3.000001");
            assertThat(saved.getMaxSpeed()).isEqualByComparingTo("9.123456");
            assertThat(saved.getPeriodStart()).isEqualByComparingTo("0.123456");
            assertThat(saved.getPeriodEnd()).isEqualByComparingTo("60.123456");
            assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
            assertThat(saved.getVersion()).isEqualTo(1);
            assertThat(saved.isCurrent()).isFalse();
        }
    }

    private void insert(JdbcTemplate jdbc, long analysis, long match, long player) {
        jdbc.update("""
                insert into player_match_metrics(analysis_id, match_id, player_id,
                total_distance, avg_speed, max_speed, period_start, period_end, version, is_current, created_at)
                values (?, ?, ?, 0, 0, 0, 0, 1, 1, false, ?)
                """, analysis, match, player, Timestamp.from(Instant.now()));
    }

    private void migrate(ConfigurableApplicationContext context, String name) throws Exception {
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource(Path.of("..", "database", "migrations", name)));
        }
    }

    private ConfigurableApplicationContext start(String ddl) {
        return new SpringApplicationBuilder(SemilleroApplication.class).profiles("test").run(
                "--spring.datasource.url=" + System.getenv("METRICS_PG_TEST_URL"),
                "--spring.datasource.username=metrics_test", "--spring.datasource.password=",
                "--spring.jpa.hibernate.ddl-auto=" + ddl,
                "--server.port=0", "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--analysis.enabled=false", "--analysis.worker-enabled=false",
                "--video.youtube.enabled=false", "--video.youtube.worker-enabled=false");
    }
}

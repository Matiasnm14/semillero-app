package edu.upb.semillero;

import edu.upb.semillero.repository.repos.ClubRepository;
import edu.upb.semillero.config.DatabaseMigrationConfiguration;
import java.sql.Connection;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.assertj.core.api.Assertions.*;

/** Destructive tests restricted to a named, disposable local database. */
@EnabledIfEnvironmentVariable(named = "MIGRATIONS_PG_TEST_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/migrations_test")
class DatabaseMigrationsPostgresTest {
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetDisposableSchema() {
        source = new DriverManagerDataSource(System.getenv("MIGRATIONS_PG_TEST_URL"), "migrations_test", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("drop schema public cascade");
        jdbc.execute("create schema public");
    }

    @Test
    void createsEmptyDatabaseBeforeJpaValidationAndDoesNotRepeatOnRestart() {
        try (var context = start(true, "validate")) {
            assertThat(context.getBean(ClubRepository.class).count()).isZero();
            assertCurrent();
            jdbc.update("insert into clubes(nombre_club) values ('Conservar')");
        }
        var history = jdbc.queryForList("select version, checksum, installed_on from flyway_schema_history order by installed_rank");
        try (var context = start(true, "validate")) {
            assertThat(context.getBean(ClubRepository.class).count()).isEqualTo(1);
            assertThat(jdbc.queryForList("select version, checksum, installed_on from flyway_schema_history order by installed_rank"))
                    .isEqualTo(history);
            assertCurrent();
        }
    }

    @Test
    void adoptsLegacyBaseAndCreatesRemainingTablesWithoutLosingData() throws Exception {
        legacy(0);
        jdbc.update("insert into clubes(nombre_club) values ('Existente')");
        try (var ignored = start(true, "validate")) {
            assertCurrent();
            assertThat(jdbc.queryForObject("select nombre_club from clubes", String.class)).isEqualTo("Existente");
            assertBaseline();
        }
    }

    @Test
    void upgradesThrough003AndAutomaticallyAdds004PreservingCompletedJobs() throws Exception {
        legacy(3);
        legacyJob(false);
        try (var ignored = start(true, "validate")) {
            assertCurrent();
            assertBaseline();
            assertThat(jdbc.queryForObject("select estado from analysis_jobs where id=50", String.class)).isEqualTo("COMPLETADO");
            assertThat(jdbc.queryForObject("select resultado_ref from analysis_jobs where id=50", String.class)).isEqualTo("legacy:50");
            assertThat(jdbc.queryForObject("select respuesta_json from analysis_jobs where id=50", String.class)).isNull();
            assertThat(jdbc.queryForObject("select count(*) from videos_partido", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void adoptsFullyMigratedDockerDatabaseWithoutDuplicatingConstraintsOrChangingJson() throws Exception {
        legacy(4);
        legacyJob(true);
        int constraints = jdbc.queryForObject("select count(*) from pg_constraint where conrelid='analysis_jobs'::regclass", Integer.class);
        try (var ignored = start(true, "validate")) {
            assertCurrent();
            assertBaseline();
            assertThat(jdbc.queryForObject("select respuesta_json from analysis_jobs where id=50", String.class))
                    .isEqualTo("[{\"dorsal\":9,\"distancia\":12.123456789}]");
            assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conrelid='analysis_jobs'::regclass", Integer.class))
                    .isEqualTo(constraints);
        }
    }

    @Test
    void completesHibernateCreatedSchemaIncludingCompositeForeignKeyAndPartialIndex() {
        try (var ignored = start(false, "create")) {
            jdbc.update("insert into clubes(nombre_club) values ('Desde dev')");
        }
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname='fk_pmm_participation'", Integer.class))
                .isZero();
        try (var ignored = start(true, "validate")) {
            assertCurrent();
            assertBaseline();
            assertThat(jdbc.queryForObject("select nombre_club from clubes", String.class)).isEqualTo("Desde dev");
            assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname='fk_pmm_participation'", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("select indexdef from pg_indexes where indexname='uk_analysis_active_video'", String.class))
                    .contains("UNIQUE", "WHERE");
        }
    }

    @Test
    void refusesUnknownSchemaBeforeCreatingHistoryOrProjectTables() {
        jdbc.execute("create table ajeno(id integer primary key, dato text)");
        jdbc.update("insert into ajeno values (1,'conservar')");
        assertThatThrownBy(() -> start(true, "validate")).hasStackTraceContaining("Esquema existente no reconocido");
        assertThat(jdbc.queryForObject("select dato from ajeno", String.class)).isEqualTo("conservar");
        assertThat(jdbc.queryForObject("select count(*) from pg_tables where schemaname='public'", Integer.class)).isEqualTo(1);
    }

    @Test
    void rollsBackFailingMigrationAndKeepsExistingRowsForCorrection() throws Exception {
        legacy(2);
        legacyJob(false);
        jdbc.execute("alter table analysis_jobs drop constraint chk_analysis_state");
        jdbc.execute("alter table analysis_jobs drop constraint chk_analysis_percentage");
        jdbc.update("update analysis_jobs set porcentaje=200 where id=50");
        assertThatThrownBy(() -> start(true, "validate")).hasStackTraceContaining("chk_analysis_percentage");
        assertThat(jdbc.queryForObject("select porcentaje from analysis_jobs where id=50", Double.class)).isEqualTo(200);
        // The state check was added earlier within V002; its absence proves DDL was rolled back too.
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname='chk_analysis_state'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version::integer=2", Integer.class)).isZero();
        jdbc.update("update analysis_jobs set porcentaje=20 where id=50");
        try (var ignored = start(true, "validate")) { assertCurrent(); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void serializesConcurrentMigrationsAndLegacyAdoption(boolean existing) throws Exception {
        if (existing) legacy(3);
        // Independent Flyway instances exercise the exact startup strategy without racing the
        // JVM-global logging setup of two SpringApplication instances inside one test process.
        try (var context = new AnnotationConfigApplicationContext(DatabaseMigrationConfiguration.class);
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var strategy = context.getBean(FlywayMigrationStrategy.class);
            Flyway firstFlyway = Flyway.configure().dataSource(source).defaultSchema("public")
                    .schemas("public").baselineVersion("-1").load();
            Flyway secondFlyway = Flyway.configure().dataSource(source).defaultSchema("public")
                    .schemas("public").baselineVersion("-1").load();
            var first = executor.submit(() -> strategy.migrate(firstFlyway));
            var second = executor.submit(() -> strategy.migrate(secondFlyway));
            first.get(45, TimeUnit.SECONDS);
            second.get(45, TimeUnit.SECONDS);
            assertCurrent();
            assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history", Integer.class)).isEqualTo(existing ? 6 : 5);
        }
        try (var ignored = start(true, "validate")) { assertCurrent(); }
    }

    @Test
    void rejectsChangedChecksumsInsteadOfRepairingHistory() {
        try (var ignored = start(true, "validate")) { assertCurrent(); }
        jdbc.update("insert into clubes(nombre_club) values ('No borrar')");
        jdbc.update("update flyway_schema_history set checksum=checksum+1 where version::integer=4");
        assertThatThrownBy(() -> start(true, "validate")).hasStackTraceContaining("checksum");
        assertThat(jdbc.queryForObject("select nombre_club from clubes", String.class)).isEqualTo("No borrar");
    }

    private void assertCurrent() {
        assertThat(jdbc.queryForObject("select count(*) from pg_tables where schemaname='public'", Integer.class)).isEqualTo(21);
        assertThat(jdbc.queryForList("select version::integer from flyway_schema_history where type='SQL' and success order by installed_rank", Integer.class))
                .containsExactly(0, 1, 2, 3, 4);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where type='SQL' and checksum is not null", Integer.class))
                .isEqualTo(5);
    }

    private void assertBaseline() {
        assertThat(jdbc.queryForObject("select version from flyway_schema_history where type='BASELINE'", String.class)).isEqualTo("-1");
    }

    private void legacy(int lastVersion) throws Exception {
        String[] names = {"000_base.sql", "001_videos_partido.sql", "002_analysis_jobs.sql",
                "003_player_match_metrics.sql", "004_analysis_future_response.sql"};
        try (Connection connection = source.getConnection()) {
            for (int i=0; i<=lastVersion; i++) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/legacy/" + names[i]));
            }
        }
    }

    private void legacyJob(boolean withJson) {
        jdbc.update("insert into clubes(id,nombre_club) values (50,'Local'),(51,'Visita')");
        jdbc.update("insert into partidos(id,fecha_inicio,club_local_id,club_visitante_id,estado) values (50,current_timestamp,50,51,'FINALIZADO')");
        jdbc.update("""
                insert into videos_partido(id,partido_id,storage_id,nombre_original,formato,tamano_bytes,sha256,
                fecha_carga,titulo_youtube,estado_youtube) values (50,50,'00000000-0000-0000-0000-000000000050',
                'legacy.mp4','mp4',1,repeat('a',64),current_timestamp,'2026-10-21','DESHABILITADO')
                """);
        jdbc.update("""
                insert into analysis_jobs(id,video_partido_id,estado,fecha_solicitud,fecha_inicio,fecha_fin,
                fecha_actualizacion,resultados_disponibles,resultado_ref) values (50,50,'COMPLETADO',
                current_timestamp,current_timestamp,current_timestamp,current_timestamp,true,'legacy:50')
                """);
        if (withJson) jdbc.update("update analysis_jobs set respuesta_json=? where id=50", "[{\"dorsal\":9,\"distancia\":12.123456789}]");
    }

    private ConfigurableApplicationContext start(boolean migrations, String ddl) {
        return new SpringApplicationBuilder(SemilleroApplication.class).profiles("test").run(
                "--spring.datasource.url=" + System.getenv("MIGRATIONS_PG_TEST_URL"),
                "--spring.datasource.username=migrations_test", "--spring.datasource.password=",
                "--spring.flyway.enabled=" + migrations, "--spring.jpa.hibernate.ddl-auto=" + ddl,
                "--server.port=0", "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--analysis.enabled=false", "--analysis.worker-enabled=false",
                "--video.youtube.enabled=false", "--video.youtube.worker-enabled=false");
    }
}

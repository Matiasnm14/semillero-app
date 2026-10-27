package edu.upb.semillero;

import com.sun.net.httpserver.HttpServer;
import edu.upb.semillero.repository.entity.*;
import edu.upb.semillero.repository.enums.*;
import edu.upb.semillero.repository.repos.*;
import edu.upb.semillero.service.AnalysisJobService;
import edu.upb.semillero.service.VideoPartidoService;
import edu.upb.semillero.service.analysis.AnalysisWorker;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.assertj.core.api.Assertions.*;

/** Opt-in, destructive only within this explicitly named disposable local database. */
@EnabledIfEnvironmentVariable(named = "ANALYSIS_PG_TEST_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/analysis_hu0203_test")
class AnalysisPostgresIntegrationTest {
    @TempDir Path root;

    @Test
    void storesFutureResultAndRecoversPendingJobsWithoutResubmittingInterruptedEvaluations() throws Exception {
        AtomicInteger submissions = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> reply = new java.util.concurrent.atomic.AtomicReference<>();
        AtomicBoolean hold = new AtomicBoolean();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        String payload = "[{\"dorsal\":9,\"distancia\":123.4567890123}]";
        reply.set(payload);
        ExecutorService remoteThreads = Executors.newVirtualThreadPerTaskExecutor();
        HttpServer remote = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        remote.setExecutor(remoteThreads);
        remote.createContext("/internal/v1/evaluaciones", exchange -> {
            try {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(exchange.getRequestHeaders().getFirst("X-Analysis-Id")).isNotBlank();
                assertThat(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1))
                        .contains("filename=\"video.mp4\"", "ftyp").doesNotContain("partidoId", "torneoId");
                submissions.incrementAndGet();
                if (hold.get()) { entered.countDown(); release.await(10, TimeUnit.SECONDS); }
                byte[] body = reply.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (java.io.IOException e) { /* The client may cancel a pending evaluation during shutdown. */ }
            finally { exchange.close(); }
        });
        remote.start();
        long completedId, pendingId, crashedId;
        try {
            try (var context = start("none", remote.getAddress().getPort())) {
                var jdbc = context.getBean(JdbcTemplate.class);
                jdbc.execute("drop schema public cascade");
                jdbc.execute("create schema public");
                try (var connection = context.getBean(DataSource.class).getConnection()) {
                    for (String name : new String[]{"000_base.sql", "001_videos_partido.sql", "002_analysis_jobs.sql",
                            "003_player_match_metrics.sql"}) {
                        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/legacy/" + name));
                    }
                    // Preserve a completed job from the former snapshot protocol across migration 004.
                    jdbc.update("insert into clubes(id, nombre_club) values (501,'Legacy Local'),(502,'Legacy Visita')");
                    jdbc.update("insert into partidos(id, fecha_inicio, club_local_id, club_visitante_id, estado) values (501,current_timestamp,501,502,'FINALIZADO')");
                    jdbc.update("""
                            insert into videos_partido(id, partido_id, storage_id, nombre_original, formato, tamano_bytes,
                            sha256, fecha_carga, titulo_youtube, estado_youtube)
                            values (501,501,'00000000-0000-0000-0000-000000000501','legacy.mp4','mp4',1,
                            repeat('a',64),current_timestamp,'2026-10-21','DESHABILITADO')
                            """);
                    jdbc.update("""
                            insert into analysis_jobs(id, video_partido_id, estado, fecha_solicitud, fecha_inicio,
                            fecha_fin, fecha_actualizacion, resultados_disponibles, resultado_ref)
                            values (501,501,'COMPLETADO',current_timestamp,current_timestamp,current_timestamp,
                            current_timestamp,true,'legacy:501')
                            """);
                    ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/legacy/004_analysis_future_response.sql"));
                }
            }
            long match;
            try (var context = start("validate", remote.getAddress().getPort())) {
                match = match(context);
                long video = upload(context, match);
                var service = context.getBean(AnalysisJobService.class);
                assertThat(service.findById(501L).resultadosDisponibles()).isTrue();
                assertThat(service.findById(501L).respuestaRecibida()).isFalse();
                completedId = context.getBean(JdbcTemplate.class).queryForObject("select id from analysis_jobs where video_partido_id=?", Long.class, video);
                assertThat(submissions.get()).isEqualTo(1);
                assertThatThrownBy(() -> service.create(match, video))
                        .isInstanceOf(edu.upb.semillero.service.exception.AnalysisConflictException.class);
                var jdbc = context.getBean(JdbcTemplate.class);
                assertThat(context.getBean(AnalysisWorker.class).processNext()).isFalse();
                await(() -> service.findById(completedId).estado().isTerminal());
                var response = service.findById(completedId);
                assertThat(response.estado()).isEqualTo(EstadoAnalisis.COMPLETADO);
                assertThat(response.respuestaRecibida()).isTrue();
                assertThat(response.resultadosDisponibles()).isFalse();
                assertThat(response.porcentaje()).isNull();
                assertThat(context.getBean(AnalysisJobRepository.class).findById(completedId).orElseThrow().getRespuestaJson())
                        .isEqualTo(payload);
                assertThatThrownBy(() -> jdbc.update("update analysis_jobs set respuesta_json='42' where id=?", completedId))
                        .isInstanceOf(DataIntegrityViolationException.class);
                pendingId = service.create(match, legacyVideo(context, match)).id();
                assertThatThrownBy(() -> jdbc.update("""
                        insert into analysis_jobs(video_partido_id, estado, fecha_solicitud, fecha_actualizacion, proxima_ejecucion)
                        select video_partido_id, estado, fecha_solicitud, fecha_actualizacion, proxima_ejecucion
                        from analysis_jobs where id=?
                        """, pendingId)).isInstanceOf(DuplicateKeyException.class);
            }
            hold.set(true);
            try (var context = start("validate", remote.getAddress().getPort())) {
                var service = context.getBean(AnalysisJobService.class);
                assertThat(service.findById(completedId).respuestaRecibida()).isTrue();
                assertThat(service.findById(pendingId).estado()).isEqualTo(EstadoAnalisis.PENDIENTE);
                context.getBean(AnalysisWorker.class).processNext();
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(service.findById(pendingId).estado()).isEqualTo(EstadoAnalisis.PROCESANDO);
                // Closing the context cannot resume the same network Future in a later process.
            }
            release.countDown();
            try (var context = start("validate", remote.getAddress().getPort())) {
                var service = context.getBean(AnalysisJobService.class);
                assertThat(service.findById(pendingId).estado()).isEqualTo(EstadoAnalisis.ERROR);
                assertThat(service.findById(pendingId).codigoError()).isEqualTo("EVALUACION_INTERRUMPIDA");
                assertThat(service.findById(completedId).respuestaRecibida()).isTrue();
                assertThat(context.getBean(AnalysisWorker.class).processNext()).isFalse();
                assertThat(submissions.get()).isEqualTo(2);
                crashedId = service.create(match, legacyVideo(context, match)).id();
                context.getBean(JdbcTemplate.class).update("""
                        update analysis_jobs set estado='PROCESANDO', enviado=true, fecha_inicio=current_timestamp,
                        claim_token='crashed-process', lease_until=current_timestamp - interval '1 minute',
                        proxima_ejecucion=current_timestamp - interval '1 minute' where id=?
                        """, crashedId);
            }
            try (var context = start("validate", remote.getAddress().getPort())) {
                context.getBean(AnalysisWorker.class).processNext();
                assertThat(context.getBean(AnalysisJobService.class).findById(crashedId).codigoError())
                        .isEqualTo("EVALUACION_INTERRUMPIDA");
                assertThat(submissions.get()).isEqualTo(2);
                assertThat(context.getBean(PlayerMatchMetricsRepository.class).count()).isZero();
                var jdbc = context.getBean(JdbcTemplate.class);
                long videosBefore = jdbc.queryForObject("select count(*) from videos_partido", Long.class);
                long analysesBefore = jdbc.queryForObject("select count(*) from analysis_jobs", Long.class);
                long filesBefore = fileCount();
                hold.set(false);
                reply.set("null");
                assertThatThrownBy(() -> upload(context, match))
                        .isInstanceOf(edu.upb.semillero.service.exception.AnalysisUploadException.class);
                assertThat(jdbc.queryForObject("select count(*) from videos_partido", Long.class)).isEqualTo(videosBefore);
                assertThat(jdbc.queryForObject("select count(*) from analysis_jobs", Long.class)).isEqualTo(analysesBefore);
                assertThat(fileCount()).isEqualTo(filesBefore);
                reply.set(payload);
                // PostgreSQL really rejects the transaction during commit, after both saveAndFlush calls.
                jdbc.execute("""
                        create function reject_test_video_commit() returns trigger language plpgsql as $$
                        begin
                            if new.nombre_original = 'commit-failure.mp4' then
                                raise exception 'simulated deferred commit rejection' using errcode='23514';
                            end if;
                            return new;
                        end $$
                        """);
                jdbc.execute("""
                        create constraint trigger reject_test_video_commit after insert on videos_partido
                        deferrable initially deferred for each row execute function reject_test_video_commit()
                        """);
                try (var resource = getClass().getResourceAsStream("/video/valid.mp4")) {
                    byte[] bytes = resource.readAllBytes();
                    assertThatThrownBy(() -> context.getBean(VideoPartidoService.class).create(match,
                            "commit-failure.mp4", bytes.length, new ByteArrayInputStream(bytes)))
                            .isInstanceOf(RuntimeException.class);
                }
                assertThat(jdbc.queryForObject("select count(*) from videos_partido", Long.class)).isEqualTo(videosBefore);
                assertThat(jdbc.queryForObject("select count(*) from analysis_jobs", Long.class)).isEqualTo(analysesBefore);
                assertThat(fileCount()).isEqualTo(filesBefore);
                assertThat(submissions.get()).isEqualTo(4);

            }
        } finally {
            release.countDown(); remote.stop(0); remoteThreads.shutdownNow();
        }
    }

    private ConfigurableApplicationContext start(String ddl, int remotePort) {
        return new SpringApplicationBuilder(SemilleroApplication.class).profiles("test").run(
                "--spring.datasource.url=" + System.getenv("ANALYSIS_PG_TEST_URL"),
                "--spring.datasource.username=analysis_test", "--spring.datasource.password=",
                "--spring.flyway.enabled=" + "validate".equals(ddl),
                "--spring.jpa.hibernate.ddl-auto=" + ddl,
                "--server.port=0", "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--analysis.enabled=true", "--analysis.base-url=http://127.0.0.1:" + remotePort,
                "--analysis.connect-timeout=1s", "--analysis.request-timeout=5s",
                "--analysis.worker-enabled=false", "--video.youtube.enabled=false", "--video.youtube.worker-enabled=false",
                "--video.storage-dir=" + root.toAbsolutePath());
    }

    private long match(ConfigurableApplicationContext context) {
        var clubs = context.getBean(ClubRepository.class);
        var local = new Club(); local.setNombreClub("Local");
        var visitor = new Club(); visitor.setNombreClub("Visitante");
        var match = new Partido();
        match.setClubLocal(clubs.saveAndFlush(local)); match.setClubVisitante(clubs.saveAndFlush(visitor));
        match.setFechaInicio(LocalDateTime.parse("2026-10-20T10:00:00")); match.setEstado(EstadoPartido.FINALIZADO);
        return context.getBean(PartidoRepository.class).saveAndFlush(match).getId();
    }

    private long upload(ConfigurableApplicationContext context, long partidoId) throws Exception {
        try (var resource = getClass().getResourceAsStream("/video/valid.mp4")) {
            byte[] video = resource.readAllBytes();
            return context.getBean(VideoPartidoService.class)
                    .create(partidoId, "partido.mp4", video.length, new ByteArrayInputStream(video)).id();
        }
    }

    private long legacyVideo(ConfigurableApplicationContext context, long match) throws Exception {
        var video = new VideoPartido();
        video.setPartido(context.getBean(PartidoRepository.class).findById(match).orElseThrow());
        video.setStorageId(java.util.UUID.randomUUID().toString());
        video.setNombreOriginal("legacy-worker.mp4"); video.setFormato("mp4");
        video.setSha256("b".repeat(64)); video.setFechaCarga(Instant.now());
        video.setTituloYoutube("2026-10-20"); video.setEstadoYoutube(EstadoYoutube.DESHABILITADO);
        var original = context.getBean(edu.upb.semillero.service.video.LocalVideoStorage.class).original(video.getStorageId());
        java.nio.file.Files.createDirectories(original.getParent());
        try (var input = getClass().getResourceAsStream("/video/valid.mp4")) {
            byte[] bytes = input.readAllBytes();
            java.nio.file.Files.write(original, bytes); video.setTamanoBytes((long) bytes.length);
        }
        return context.getBean(VideoPartidoRepository.class).saveAndFlush(video).getId();
    }

    private long fileCount() throws Exception {
        try (var files = java.nio.file.Files.walk(root)) { return files.filter(java.nio.file.Files::isRegularFile).count(); }
    }

    private void await(java.util.function.BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) { if (condition.getAsBoolean()) return; Thread.sleep(10); }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}

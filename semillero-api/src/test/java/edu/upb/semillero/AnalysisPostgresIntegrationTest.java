package edu.upb.semillero;

import com.sun.net.httpserver.HttpServer;
import edu.upb.semillero.repository.dto.response.AnalysisJobResponseDto;
import edu.upb.semillero.repository.entity.*;
import edu.upb.semillero.repository.enums.*;
import edu.upb.semillero.repository.repos.*;
import edu.upb.semillero.service.AnalysisJobService;
import edu.upb.semillero.service.VideoPartidoService;
import edu.upb.semillero.service.analysis.*;
import edu.upb.semillero.service.exception.AnalysisConflictException;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

/** Opt-in: the URL must name a disposable local database. It is recreated by this test. */
@EnabledIfEnvironmentVariable(named = "ANALYSIS_PG_TEST_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/analysis_hu0203_test")
class AnalysisPostgresIntegrationTest {
    @TempDir Path root;

    @Test
    void migratesValidatesPreventsDuplicatesAndRecoversAcrossRealApplicationRestarts() throws Exception {
        var json = new ObjectMapper();
        var remoteIds = ConcurrentHashMap.<Long>newKeySet();
        var submissions = new AtomicInteger();
        var failFirstReply = new AtomicBoolean(true);
        var finished = new AtomicBoolean(false);
        Instant started = Instant.now(); // Includes nanoseconds; SQL preserves microseconds.
        HttpServer remote = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        remote.createContext("/internal/v1/analisis/", exchange -> {
            long id = Long.parseLong(exchange.getRequestURI().getPath().replaceAll(".*/", ""));
            int status = 200;
            if (exchange.getRequestMethod().equals("PUT")) {
                var input = json.readTree(exchange.getRequestBody().readAllBytes());
                assertThat(input.path("analysisId").asLong()).isEqualTo(id);
                remoteIds.add(id);
                submissions.incrementAndGet();
                // The remote accepted the job but the backend cannot confirm the first response.
                if (failFirstReply.getAndSet(false)) status = 503;
            }
            var snapshot = new AnalysisGateway.Snapshot(id, finished.get() ? 2L : 1L,
                    finished.get() ? EstadoAnalisis.COMPLETADO : EstadoAnalisis.PROCESANDO,
                    finished.get() ? 100.0 : 40.0, finished.get() ? 100L : 40L, 100L, started,
                    finished.get() ? Instant.now() : null, finished.get(), finished.get() ? "resultados:" + id : null, null);
            byte[] body = json.writeValueAsBytes(snapshot);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        remote.start();
        long jobId;
        long partidoId;
        long videoId;
        try {
            try (var context = start("create", remote.getAddress().getPort())) {
                var jdbc = context.getBean(JdbcTemplate.class);
                jdbc.execute("drop table player_match_metrics");
                jdbc.execute("drop table analysis_jobs");
                jdbc.execute("drop table videos_partido");
                try (var connection = context.getBean(DataSource.class).getConnection()) {
                    ScriptUtils.executeSqlScript(connection, new FileSystemResource(Path.of("..", "database", "migrations", "001_videos_partido.sql")));
                    ScriptUtils.executeSqlScript(connection, new FileSystemResource(Path.of("..", "database", "migrations", "002_analysis_jobs.sql")));
                    ScriptUtils.executeSqlScript(connection, new FileSystemResource(Path.of("..", "database", "migrations", "003_player_match_metrics.sql")));
                }
            }
            try (var context = start("validate", remote.getAddress().getPort())) {
                partidoId = match(context);
                videoId = upload(context, partidoId);
                var service = context.getBean(AnalysisJobService.class);
                jobId = service.create(partidoId, videoId).id();
                var jdbc = context.getBean(JdbcTemplate.class);
                assertThatThrownBy(() -> jdbc.update("""
                        insert into analysis_jobs(video_partido_id, estado, fecha_solicitud, fecha_actualizacion, proxima_ejecucion)
                        select video_partido_id, estado, fecha_solicitud, fecha_actualizacion, proxima_ejecucion
                        from analysis_jobs where id = ?
                        """, jobId)).isInstanceOf(DuplicateKeyException.class);
                assertThatThrownBy(() -> jdbc.update("update analysis_jobs set porcentaje=101 where id=?", jobId))
                        .isInstanceOf(DataIntegrityViolationException.class);

                long otherVideo = upload(context, partidoId);
                var start = new CountDownLatch(1);
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    Callable<Object> create = () -> {
                        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                        try { return service.create(partidoId, otherVideo); }
                        catch (AnalysisConflictException e) { return e; }
                    };
                    var first = executor.submit(create);
                    var second = executor.submit(create);
                    start.countDown();
                    var responses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
                    assertThat(responses).filteredOn(AnalysisJobResponseDto.class::isInstance).hasSize(1);
                    long otherJob = responses.stream().filter(AnalysisJobResponseDto.class::isInstance)
                            .map(AnalysisJobResponseDto.class::cast).findFirst().orElseThrow().id();
                    // Keep the independent job out of this recovery scenario.
                    jdbc.update("update analysis_jobs set proxima_ejecucion = current_timestamp + interval '1 day' where id=?", otherJob);
                }

                assertThat(context.getBean(AnalysisWorker.class).processNext()).isTrue();
                assertThat(service.findById(jobId).estado()).isEqualTo(EstadoAnalisis.PENDIENTE);
                jdbc.update("""
                        update analysis_jobs set claim_token='interrupted-owner',
                        lease_until=current_timestamp - interval '1 minute',
                        proxima_ejecucion=current_timestamp - interval '1 minute' where id=?
                        """, jobId);
            }
            try (var context = start("validate", remote.getAddress().getPort())) {
                var service = context.getBean(AnalysisJobService.class);
                var worker = context.getBean(AnalysisWorker.class);
                assertThat(service.findById(jobId).estado()).isEqualTo(EstadoAnalisis.PENDIENTE);
                assertThat(worker.processNext()).isTrue();
                assertThat(service.findById(jobId).estado()).isEqualTo(EstadoAnalisis.PROCESANDO);
                assertThat(remoteIds).isEqualTo(Set.of(jobId));
                assertThat(submissions.get()).isEqualTo(2);
                finished.set(true);
                context.getBean(JdbcTemplate.class).update(
                        "update analysis_jobs set proxima_ejecucion=current_timestamp - interval '1 minute' where id=?", jobId);
                worker.processNext();
                var response = service.findById(jobId);
                assertThat(response.estado()).isEqualTo(EstadoAnalisis.COMPLETADO);
                assertThat(response.resultadosDisponibles()).isTrue();
                assertThat(response.fechaFin()).isNotNull();
                assertThat(response.videoId()).isEqualTo(videoId);
                assertThat(response.partidoId()).isEqualTo(partidoId);
                assertThat(submissions.get()).isEqualTo(2);
            }
            try (var context = start("validate", remote.getAddress().getPort())) {
                var saved = context.getBean(AnalysisJobService.class).findById(jobId);
                assertThat(saved.estado()).isEqualTo(EstadoAnalisis.COMPLETADO);
                assertThat(saved.resultadosDisponibles()).isTrue();
                assertThat(context.getBean(AnalysisWorker.class).processNext()).isFalse();
            }
        } finally { remote.stop(0); }
    }

    private ConfigurableApplicationContext start(String ddl, int remotePort) {
        return new SpringApplicationBuilder(SemilleroApplication.class).profiles("test").run(
                "--spring.datasource.url=" + System.getenv("ANALYSIS_PG_TEST_URL"),
                "--spring.datasource.username=analysis_test", "--spring.datasource.password=",
                "--spring.jpa.hibernate.ddl-auto=" + ddl,
                "--server.port=0", "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--analysis.enabled=true", "--analysis.base-url=http://127.0.0.1:" + remotePort,
                "--analysis.worker-enabled=false", "--video.youtube.enabled=false", "--video.youtube.worker-enabled=false",
                "--video.storage-dir=" + root.toAbsolutePath());
    }

    private long match(ConfigurableApplicationContext context) {
        var clubs = context.getBean(ClubRepository.class);
        var local = new Club(); local.setNombreClub("Local");
        var visitor = new Club(); visitor.setNombreClub("Visitante");
        var match = new Partido();
        match.setClubLocal(clubs.saveAndFlush(local));
        match.setClubVisitante(clubs.saveAndFlush(visitor));
        match.setFechaInicio(LocalDateTime.parse("2026-10-20T10:00:00"));
        match.setEstado(EstadoPartido.FINALIZADO);
        return context.getBean(PartidoRepository.class).saveAndFlush(match).getId();
    }

    private long upload(ConfigurableApplicationContext context, long partidoId) throws Exception {
        try (var resource = getClass().getResourceAsStream("/video/valid.mp4")) {
            byte[] video = resource.readAllBytes();
            return context.getBean(VideoPartidoService.class)
                    .create(partidoId, "partido.mp4", video.length, new ByteArrayInputStream(video)).id();
        }
    }
}

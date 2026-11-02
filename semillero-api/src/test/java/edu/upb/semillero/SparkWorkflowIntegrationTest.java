package edu.upb.semillero;

import com.sun.net.httpserver.HttpServer;
import edu.upb.semillero.repository.entity.*;
import edu.upb.semillero.repository.enums.*;
import edu.upb.semillero.repository.repos.*;
import edu.upb.semillero.service.analysis.AnalysisWorker;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:spark-workflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "analysis.enabled=true", "analysis.worker-enabled=false", "analysis.poll-interval=20ms",
        "video.youtube.enabled=false", "video.youtube.worker-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SparkWorkflowIntegrationTest {
    @TempDir static Path root;
    private static final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();
    private static final AtomicReference<String> remoteId = new AtomicReference<>();
    private static final AtomicReference<JdbcTemplate> database = new AtomicReference<>();
    private static final AtomicBoolean deletedAfterCommit = new AtomicBoolean();
    private static final HttpServer remote = remote();
    private static final String RESULT = "{\"analysisId\":\"%s\",\"status\":\"COMPLETED\",\"summary\":{\"framesProcessed\":9243},\"players\":[{\"playerId\":\"results:shot0:track10484:segment0:players\",\"jerseyNumber\":7,\"jerseyStatus\":\"consensus_unverified\",\"team\":null,\"movement\":{\"distancePixels\":655.11,\"distanceMeters\":null,\"averageSpeedKmh\":null,\"metricStatus\":\"unavailable_missing_calibration\"}}]}";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("video.storage-dir", () -> root.resolve("videos").toString());
        registry.add("analysis.artifacts-dir", () -> root.resolve("artifacts").toString());
        registry.add("analysis.base-url", () -> "http://127.0.0.1:" + remote.getAddress().getPort());
        registry.add("analysis.token", () -> "spark-test-only");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClubRepository clubs;
    @Autowired PartidoRepository matches;
    @Autowired AnalysisJobRepository jobs;
    @Autowired PlayerMatchMetricsRepository metrics;
    @Autowired AnalysisWorker worker;
    @Autowired JdbcTemplate jdbc;

    private static HttpServer remote() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(threads);
            server.createContext("/analyses", exchange -> {
                try {
                    if (!"Bearer spark-test-only".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                        exchange.sendResponseHeaders(401, -1); return;
                    }
                    String path = exchange.getRequestURI().getPath();
                    byte[] body;
                    int status = 200;
                    String type = "application/json";
                    if (exchange.getRequestMethod().equals("POST")) {
                        String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
                        var matcher = java.util.regex.Pattern.compile("name=\"analysisId\"\r\n\r\n([^\r]+)").matcher(sent);
                        if (!matcher.find()) { exchange.sendResponseHeaders(400, -1); return; }
                        remoteId.set(matcher.group(1));
                        status = 202;
                        body = ("{\"analysisId\":\"" + remoteId.get() + "\",\"status\":\"QUEUED\"}").getBytes(StandardCharsets.UTF_8);
                    } else if (exchange.getRequestMethod().equals("DELETE")) {
                        deletedAfterCommit.set(database.get().queryForObject("select count(*) from analysis_jobs where remote_analysis_id=? and estado='COMPLETADO' and respuesta_json is not null", Integer.class, remoteId.get()) == 1);
                        exchange.sendResponseHeaders(204, -1); return;
                    } else if (path.endsWith("/heatmap")) {
                        type = "image/png";
                        try { body = HttpAnalysisGatewayTest.png(); } catch (Exception e) { throw new java.io.IOException(e); }
                    } else if (path.endsWith("/results")) {
                        body = RESULT.formatted(remoteId.get()).getBytes(StandardCharsets.UTF_8);
                    } else {
                        body = ("{\"analysisId\":\"" + remoteId.get() + "\",\"status\":\"COMPLETED\",\"progress\":null}").getBytes(StandardCharsets.UTF_8);
                    }
                    exchange.getResponseHeaders().set("Content-Type", type);
                    exchange.sendResponseHeaders(status, body.length);
                    exchange.getResponseBody().write(body);
                } finally { exchange.close(); }
            });
            server.start();
            return server;
        } catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
    }

    @AfterAll
    static void stop() { remote.stop(0); threads.shutdownNow(); }

    @Test
    void videoUploadPersistsActualSparkResultAndPngBeforeAcknowledgingCleanup() throws Exception {
        database.set(jdbc);
        var local = new Club(); local.setNombreClub("Local Spark");
        var visitor = new Club(); visitor.setNombreClub("Visita Spark");
        var match = new Partido();
        match.setClubLocal(clubs.saveAndFlush(local)); match.setClubVisitante(clubs.saveAndFlush(visitor));
        match.setFechaInicio(LocalDateTime.parse("2026-11-02T10:00:00")); match.setEstado(EstadoPartido.FINALIZADO);
        long matchId = matches.saveAndFlush(match).getId();
        byte[] video;
        try (var input = getClass().getResourceAsStream("/video/valid.mp4")) { video = input.readAllBytes(); }
        mvc.perform(multipart("/api/v1/partidos/" + matchId + "/videos").with(user("analista").roles("ADMINISTRADOR_GENERAL"))
                .file(new MockMultipartFile("file", "partido.mp4", "video/mp4", video)))
                .andExpect(status().isAccepted());
        var job = jobs.findAll().getFirst();
        long id = job.getId();
        assertThat(job.getEstado()).isEqualTo(EstadoAnalisis.PENDIENTE);
        assertThat(remoteId.get()).isNull();
        worker.processNext();
        long finishDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!jobs.findById(id).orElseThrow().getEstado().isTerminal() && System.nanoTime() < finishDeadline) Thread.sleep(10);
        job = jobs.findById(id).orElseThrow();
        assertThat(job.getRespuestaJson()).isEqualTo(RESULT.formatted(remoteId.get()));
        assertThat(job.isRemoteCleanupPending()).isTrue();
        assertThat(deletedAfterCommit.get()).isFalse();
        mvc.perform(get("/api/v1/analisis/" + id).with(user("analista").roles("ADMINISTRADOR_GENERAL"))).andExpect(jsonPath("$.heatmapDisponible").value(true))
                .andExpect(jsonPath("$.respuestaRecibida").value(true)).andExpect(jsonPath("$.resultadosDisponibles").value(false));
        mvc.perform(get("/api/v1/analisis/" + id + "/resultados")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/analisis/" + id + "/heatmap")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/analisis/" + id + "/resultados").with(user("analista").roles("ADMINISTRADOR_GENERAL")))
                .andExpect(status().isOk()).andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.players[0].jerseyStatus").value("consensus_unverified"))
                .andExpect(jsonPath("$.players[0].movement.distanceMeters").isEmpty());
        mvc.perform(get("/api/v1/analisis/" + id + "/heatmap").with(user("analista").roles("ADMINISTRADOR_GENERAL")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"analysis-" + id + "-heatmap.png\""))
                .andExpect(content().bytes(HttpAnalysisGatewayTest.png()));
        assertThat(metrics.count()).isZero();
        worker.processNext();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (jobs.findById(id).orElseThrow().isRemoteCleanupPending() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(deletedAfterCommit.get()).isTrue();
        assertThat(jobs.findById(id).orElseThrow().isRemoteCleanupPending()).isFalse();
        // Serving a saved result never calls back to a Spark that has already deleted it.
        mvc.perform(get("/api/v1/analisis/" + id + "/resultados").with(user("analista").roles("ADMINISTRADOR_GENERAL"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/analisis/" + id + "/heatmap").with(user("analista").roles("ADMINISTRADOR_GENERAL"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/analisis/999999/heatmap").with(user("analista").roles("ADMINISTRADOR_GENERAL"))).andExpect(status().isNotFound());
    }
}

package edu.upb.semillero;

import edu.upb.semillero.config.AnalysisProperties;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.enums.EstadoAnalisis;
import edu.upb.semillero.repository.enums.EstadoHeatmap;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.EstadoYoutube;
import edu.upb.semillero.repository.repos.AnalysisJobRepository;
import edu.upb.semillero.repository.repos.ClubRepository;
import edu.upb.semillero.repository.repos.PartidoRepository;
import edu.upb.semillero.repository.repos.UsuarioRepository;
import edu.upb.semillero.repository.repos.VideoPartidoRepository;
import edu.upb.semillero.service.video.LocalVideoStorage;
import edu.upb.semillero.service.analysis.AnalysisWorker;
import edu.upb.semillero.service.seguridad.JwtService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Explicit opt-in real GPU test. *IT is excluded from the normal Surefire test patterns. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "server.address=127.0.0.1",
        "analysis.enabled=true", "analysis.worker-enabled=false", "analysis.poll-interval=2s",
        "analysis.request-timeout=10m", "analysis.max-duration=20m", "analysis.max-response-bytes=16777216",
        "video.youtube.enabled=false", "video.youtube.worker-enabled=false", "logging.level.root=WARN"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPARK_LIVE_TEST", matches = "true")
class SparkLiveIT {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) {
        String url = System.getenv("SPARK_LIVE_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/spark_live_test")) {
            throw new IllegalArgumentException("La prueba real exige una base PostgreSQL local spark_live_test");
        }
        Path output = output();
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "spark_live_test");
        properties.add("spring.datasource.password", () -> "");
        properties.add("analysis.base-url", () -> System.getenv("ANALYSIS_BASE_URL"));
        properties.add("analysis.token", () -> System.getenv("ANALYSIS_TOKEN"));
        properties.add("analysis.artifacts-dir", () -> output.resolve("artifacts").toString());
        properties.add("video.storage-dir", () -> output.resolve("videos").toString());
        if (withYoutube()) {
            properties.add("analysis.worker-enabled", () -> true);
            properties.add("video.youtube.enabled", () -> true);
            properties.add("video.youtube.worker-enabled", () -> true);
            properties.add("video.youtube.poll-ms", () -> 1000);
            properties.add("video.youtube.python", () -> System.getenv("YOUTUBE_LIVE_PYTHON"));
            properties.add("video.youtube.script", () -> System.getenv("YOUTUBE_LIVE_SCRIPT"));
            properties.add("video.youtube.client", () -> System.getenv("YOUTUBE_LIVE_CLIENT"));
            properties.add("video.youtube.tokens", () -> System.getenv("YOUTUBE_LIVE_TOKENS"));
        }
    }

    private static Path output() { return Path.of(System.getenv("SPARK_LIVE_OUTPUT")).toAbsolutePath(); }
    private static boolean withYoutube() { return "true".equals(System.getenv("YOUTUBE_LIVE_TEST")); }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClubRepository clubs;
    @Autowired PartidoRepository matches;
    @Autowired UsuarioRepository users;
    @Autowired AnalysisJobRepository jobs;
    @Autowired AnalysisWorker worker;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired AnalysisProperties properties;
    @Autowired VideoPartidoRepository videos;
    @Autowired LocalVideoStorage storage;
    @LocalServerPort int port;

    @Test
    void uploadsThroughSpringStoresRealResultsAndVerifiesAutomaticRemoteCleanup() throws Exception {
        Path video = Path.of(System.getenv("SPARK_LIVE_VIDEO"));
        long videoBytes = Files.size(video);
        assertThat(videoBytes).as("Límite multipart del backend: 2 GiB").isBetween(1L, 2L * 1024 * 1024 * 1024);
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(Files.newInputStream(video), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        Files.createDirectories(output());
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("startedAt", Instant.now().toString());
        evidence.put("video", video.getFileName().toString());
        evidence.put("videoBytes", videoBytes);
        evidence.put("videoSha256", HexFormat.of().formatHex(digest.digest()));
        try {
            var actor = new Usuario();
            actor.setName("spark-live-" + System.nanoTime());
            actor = users.saveAndFlush(actor);
            String token = jwt.emitir(actor.getId(), actor.getName(), List.of("ADMIN"), null);
            var local = new Club(); local.setNombreClub("Prueba Spark local");
            var visitor = new Club(); visitor.setNombreClub("Prueba Spark visita");
            var match = new Partido();
            match.setClubLocal(clubs.saveAndFlush(local));
            match.setClubVisitante(clubs.saveAndFlush(visitor));
            match.setFechaInicio(LocalDateTime.now(ZoneId.of("America/La_Paz")));
            match.setEstado(EstadoPartido.FINALIZADO);
            long matchId = matches.saveAndFlush(match).getId();
            String boundary = "SemilleroLive" + UUID.randomUUID().toString().replace("-", "");
            String prefix = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"prueba-spark.mp4\"\r\nContent-Type: video/mp4\r\n\r\n";
            var body = HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofString(prefix),
                    HttpRequest.BodyPublishers.ofFile(video),
                    HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n"));
            long id;
            long videoId;
            try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
                var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/partidos/" + matchId + "/videos"))
                        .timeout(Duration.ofMinutes(10)).header("Authorization", "Bearer " + token)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(body).build();
                var accepted = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(accepted.statusCode()).isEqualTo(202);
                id = json.readTree(accepted.body()).path("analysisId").asLong();
                videoId = json.readTree(accepted.body()).path("id").asLong();
                if (withYoutube()) assertThat(json.readTree(accepted.body()).path("estadoYoutube").asString()).isEqualTo("ESPERANDO_ANALISIS");
            }
            assertThat(id).isPositive();
            evidence.put("analysisId", id);
            var job = jobs.findById(id).orElseThrow();
            if (!withYoutube()) {
                assertThat(job.getEstado()).isEqualTo(EstadoAnalisis.PENDIENTE);
                worker.processNext();
            }
            long deadline = System.nanoTime() + Duration.ofMinutes(21).toNanos();
            long nextProgress = 0;
            while (!(job = jobs.findById(id).orElseThrow()).getEstado().isTerminal() && System.nanoTime() < deadline) {
                if (!withYoutube()) worker.processNext(); // The complete-flow test uses only scheduled workers.
                if (System.nanoTime() >= nextProgress) {
                    var progress = new LinkedHashMap<String, Object>();
                    progress.put("analysisId", id);
                    progress.put("remoteAnalysisId", job.getRemoteAnalysisId());
                    progress.put("estado", job.getEstado().name());
                    progress.put("porcentaje", job.getPorcentaje());
                    progress.put("segundosProcesados", job.getSegundosProcesados());
                    progress.put("segundosTotales", job.getSegundosTotales());
                    Files.writeString(output().resolve("progress.json"), json.writeValueAsString(progress));
                    nextProgress = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                }
                Thread.sleep(500);
            }
            evidence.put("remoteAnalysisId", job.getRemoteAnalysisId());
            evidence.put("estado", job.getEstado().name());
            evidence.put("estadoHeatmap", job.getEstadoHeatmap().name());
            evidence.put("codigoError", job.getCodigoError());
            assertThat(job.getEstado()).as("Resultado real: %s", job.getCodigoError()).isEqualTo(EstadoAnalisis.COMPLETADO);
            assertThat(job.getCodigoError()).isNull();
            assertThat(job.getEstadoHeatmap()).isIn(EstadoHeatmap.DISPONIBLE, EstadoHeatmap.NO_DISPONIBLE);
            assertThat(job.isRemoteCleanupPending()).isFalse();
            String saved = jdbc.queryForObject("select respuesta_json from analysis_jobs where id=?", String.class, id);
            assertThat(saved).isNotBlank();
            Files.writeString(output().resolve("results.json"), saved);
            var parsed = json.readTree(saved);
            assertThat(parsed.path("analysisId").asString()).isEqualTo(job.getRemoteAnalysisId());
            evidence.put("summary", parsed.path("summary"));
            evidence.put("playerSegments", parsed.path("players").size());
            evidence.put("jsonSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(saved.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            String path = "/api/v1/analisis/" + id;
            var result = mvc.perform(get(path + "/resultados").header("Authorization", "Bearer " + token)).andReturn().getResponse();
            assertThat(result.getStatus()).isEqualTo(200);
            assertThat(result.getContentAsString()).isEqualTo(saved);
            assertThat(mvc.perform(get(path + "/resultados")).andReturn().getResponse().getStatus()).isEqualTo(401);
            var map = mvc.perform(get(path + "/heatmap").header("Authorization", "Bearer " + token)).andReturn().getResponse();
            assertThat(map.getStatus()).isEqualTo(job.getEstadoHeatmap() == EstadoHeatmap.DISPONIBLE ? 200 : 404);
            if (map.getStatus() == 200) {
                assertThat(map.getContentType()).startsWith("image/png");
                Files.write(output().resolve("heatmap.png"), map.getContentAsByteArray());
            }
            try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
                var request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + "/analyses/" + job.getRemoteAnalysisId()))
                        .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + properties.getToken()).GET().build();
                int status;
                long cleanupDeadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                do {
                    status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                    if (status == 404) break;
                    Thread.sleep(1000);
                } while (System.nanoTime() < cleanupDeadline);
                evidence.put("remoteStatusAfterDelivery", status);
                assertThat(status).as("Spark elimina el trabajo tras entregar JSON y mapa o 422").isEqualTo(404);
            }
            // These are served locally, after the remote job no longer exists.
            assertThat(mvc.perform(get(path + "/resultados").header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString()).isEqualTo(saved);
            if (withYoutube()) {
                var publication = videos.findById(videoId).orElseThrow();
                long youtubeDeadline = System.nanoTime() + Duration.ofMinutes(8).toNanos();
                while (publication.getEstadoYoutube() != EstadoYoutube.DISPONIBLE && System.nanoTime() < youtubeDeadline) {
                    assertThat(publication.getEstadoYoutube()).as("Publicación automática: %s", publication.getErrorYoutube())
                            .isNotIn(EstadoYoutube.ERROR, EstadoYoutube.REVISION_REQUERIDA, EstadoYoutube.DESHABILITADO);
                    Thread.sleep(1000);
                    publication = videos.findById(videoId).orElseThrow();
                }
                evidence.put("videoId", videoId);
                evidence.put("storageId", publication.getStorageId());
                evidence.put("youtubeState", publication.getEstadoYoutube().name());
                evidence.put("youtubeId", publication.getYoutubeId());
                assertThat(publication.getEstadoYoutube()).isEqualTo(EstadoYoutube.DISPONIBLE);
                var receipt = json.readTree(Files.readString(storage.receipt(publication.getStorageId())));
                assertThat(receipt.path("actualPrivacy").asString()).isEqualTo("unlisted");
                assertThat(receipt.path("actualTitle").asString()).isEqualTo(match.getFechaInicio().toLocalDate().toString());
                assertThat(receipt.path("actualChannelId").asString()).isEqualTo(receipt.path("channelId").asString());
                assertThat(receipt.path("videoId").asString()).isEqualTo(publication.getYoutubeId());
                evidence.put("youtubePrivacy", receipt.path("actualPrivacy").asString());
                evidence.put("youtubeTitle", receipt.path("actualTitle").asString());
                evidence.put("scheduledWorkersOnly", true);
            }
            evidence.put("resultado", "APROBADO");
        } finally {
            evidence.put("finishedAt", Instant.now().toString());
            Files.writeString(output().resolve("report.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
        }
    }
}

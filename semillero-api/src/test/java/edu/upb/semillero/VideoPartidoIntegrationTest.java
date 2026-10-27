package edu.upb.semillero;

import edu.upb.semillero.config.VideoProperties;
import edu.upb.semillero.config.AnalysisProperties;
import edu.upb.semillero.repository.entity.AnalysisJob;
import edu.upb.semillero.repository.enums.EstadoAnalisis;
import edu.upb.semillero.repository.repos.AnalysisJobRepository;
import edu.upb.semillero.service.AnalysisJobService;
import edu.upb.semillero.service.analysis.AnalysisGateway;
import edu.upb.semillero.service.analysis.AnalysisWorker;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.VideoPartido;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.EstadoYoutube;
import edu.upb.semillero.repository.repos.ClubRepository;
import edu.upb.semillero.repository.repos.PartidoRepository;
import edu.upb.semillero.repository.repos.VideoPartidoRepository;
import edu.upb.semillero.service.VideoPartidoService;
import edu.upb.semillero.service.exception.VideoTooLargeException;
import edu.upb.semillero.service.video.LocalVideoStorage;
import edu.upb.semillero.service.video.YouTubePublicationException;
import edu.upb.semillero.service.video.YouTubePublisher;
import edu.upb.semillero.service.video.YouTubeUploadWorker;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:video-partido;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "analysis.enabled=true", "analysis.base-url=http://127.0.0.1:9999", "analysis.worker-enabled=false",
        "video.youtube.enabled=true", "video.youtube.worker-enabled=false", "video.max-bytes=100000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VideoPartidoIntegrationTest {
    @TempDir static Path root;
    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("video.storage-dir", () -> root.toString());
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PartidoRepository partidos;
    @Autowired ClubRepository clubes;
    @MockitoSpyBean VideoPartidoRepository videos;
    @Autowired VideoPartidoService service;
    @Autowired LocalVideoStorage storage;
    @Autowired VideoProperties properties;
    @Autowired YouTubeUploadWorker worker;
    @MockitoBean YouTubePublisher publisher;
    @MockitoBean AnalysisGateway gateway;
    @MockitoSpyBean AnalysisJobRepository analyses;
    @Autowired AnalysisProperties analysisProperties;
    @Autowired AnalysisWorker analysisWorker;
    @Autowired AnalysisJobService analysisService;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    private static final String PAYLOAD = "[{\"dorsal\":9,\"distancia\":12.1234567890123}]";
    private long partidoId;
    private byte[] valid;

    @BeforeEach
    void setup() throws Exception {
        analyses.deleteAll();
        videos.deleteAll();
        partidos.deleteAll();
        clubes.deleteAll();
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                if (!p.equals(root)) Files.delete(p);
            }
        }
        analysisProperties.setEnabled(true);
        analysisProperties.setMaxConcurrentJobs(1);
        analysisProperties.setRequestTimeout(java.time.Duration.ofSeconds(10));
        when(gateway.evaluate(any())).thenReturn(CompletableFuture.completedFuture(PAYLOAD));
        Club local = new Club();
        local.setNombreClub("Local");
        Club visitante = new Club();
        visitante.setNombreClub("Visitante");
        Partido partido = new Partido();
        partido.setClubLocal(clubes.saveAndFlush(local));
        partido.setClubVisitante(clubes.saveAndFlush(visitante));
        partido.setFechaInicio(LocalDateTime.parse("2026-05-02T10:00:00"));
        partido.setEstado(EstadoPartido.PROGRAMADO);
        partidoId = partidos.saveAndFlush(partido).getId();
        try (var stream = getClass().getResourceAsStream("/video/valid.mp4")) {
            valid = stream.readAllBytes();
        }
    }

    @Test
    void acceptsVideoAndFinalJsonTogetherWithoutPublishingInTheRequest() throws Exception {
        JsonNode response = upload();
        long id = response.path("id").asLong();
        assertThat(response.path("nombreOriginal").asText()).isEqualTo("partido.mp4");
        assertThat(response.path("formato").asText()).isEqualTo("mp4");
        assertThat(response.path("tamanoBytes").asLong()).isEqualTo(valid.length);
        assertThat(response.path("duracionSegundos").asDouble()).isCloseTo(1, within(0.01));
        assertThat(response.path("tituloYoutube").asText()).isEqualTo("2026-05-02");
        assertThat(response.path("estadoYoutube").asText()).isEqualTo("PENDIENTE");
        assertThat(response.path("disponibleParaAnalisis").asBoolean()).isTrue();
        assertThat(response.path("sha256").asText()).isEqualTo(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valid)));
        assertThat(response.has("storageId")).isFalse();
        assertThat(response.has("claimToken")).isFalse();
        assertThat(analyses.count()).isEqualTo(1);
        var analysis = analyses.findAll().getFirst();
        assertThat(analysis.getEstado()).isEqualTo(EstadoAnalisis.COMPLETADO);
        assertThat(analysis.getRespuestaJson()).isEqualTo(PAYLOAD);
        assertThat(analysis.isResultadosDisponibles()).isFalse();
        assertThat(analysis.isEnviado()).isTrue();
        assertThat(analysis.getClaimToken()).isNull();
        assertThat(analysis.getLeaseUntil()).isNull();
        assertThat(analysis.getProximaEjecucion()).isNull();
        assertThat(analysis.getFechaInicio()).isBetween(analysis.getFechaSolicitud(), analysis.getFechaFin());
        mvc.perform(get(base() + "/" + id + "/analisis"))
                .andExpect(jsonPath("$.content[0].estado").value("COMPLETADO"))
                .andExpect(jsonPath("$.content[0].respuestaRecibida").value(true))
                .andExpect(jsonPath("$.content[0].resultadosDisponibles").value(false));
        mvc.perform(post(base() + "/" + id + "/analisis")).andExpect(status().isConflict());
        assertThat(analysisWorker.processNext()).isFalse();
        verify(gateway, times(1)).evaluate(any());
        VideoPartido stored = videos.findById(id).orElseThrow();
        assertThat(Files.readAllBytes(storage.original(stored.getStorageId()))).isEqualTo(valid);
        verifyNoInteractions(publisher);
        mvc.perform(get(base() + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreOriginal").value("partido.mp4"));
        mvc.perform(get(base())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(delete("/api/v1/partidos/" + partidoId)).andExpect(status().isConflict());
    }

    @Test
    void rejectsUnknownMatchBeforeStoringAnyFile() throws Exception {
        mvc.perform(multipart("/api/v1/partidos/99999999/videos").file(file("clip.mp4", valid)))
                .andExpect(status().isNotFound());
        assertEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    void cannotReadVideoThroughAnotherMatch() throws Exception {
        long id = upload().path("id").asLong();
        Partido another = new Partido();
        var first = partidos.findById(partidoId).orElseThrow();
        another.setClubLocal(first.getClubLocal());
        another.setClubVisitante(first.getClubVisitante());
        another.setFechaInicio(first.getFechaInicio().plusDays(1));
        another.setEstado(EstadoPartido.PROGRAMADO);
        long other = partidos.saveAndFlush(another).getId();
        mvc.perform(get("/api/v1/partidos/" + other + "/videos/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsEmptyAndMissingFiles() throws Exception {
        mvc.perform(multipart(base()).file(file("clip.mp4", new byte[0])))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(base())).andExpect(status().isBadRequest());
        assertEmpty();
    }

    @Test
    void rejectsCorruptAndRenamedFilesEvenWithVideoMime() throws Exception {
        mvc.perform(multipart(base()).file(file("clip.mp4", "this is not a video".getBytes())))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(base()).file(file("clip.mp4", java.util.Arrays.copyOf(valid, 80))))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(base()).file(file("clip.avi", valid))).andExpect(status().isBadRequest());
        assertEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsAudioOnlyMp4() throws Exception {
        byte[] audio;
        try (var input = getClass().getResourceAsStream("/video/audio-only.mp4")) {
            audio = input.readAllBytes();
        }
        mvc.perform(multipart(base()).file(file("audio.mp4", audio))).andExpect(status().isBadRequest());
        assertEmpty();
    }

    @Test
    void rejectsCorruptPacketsEvenWhenContainerMetadataIsReadable() throws Exception {
        byte[] corrupt = valid.clone();
        int payload = new String(corrupt, java.nio.charset.StandardCharsets.ISO_8859_1).indexOf("mdat") + 704;
        java.util.Arrays.fill(corrupt, payload, payload + 50, (byte) 0xff);
        mvc.perform(multipart(base()).file(file("damaged.mp4", corrupt))).andExpect(status().isBadRequest());
        assertEmpty();
    }

    @Test
    void normalizesClientPathsWithoutUsingThemAsStoragePaths() throws Exception {
        var result = mvc.perform(multipart(base()).file(file("../../unsafe\\clip.MP4", valid)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nombreOriginal").value("clip.MP4"))
                .andReturn().getResponse().getContentAsString();
        var video = videos.findById(json.readTree(result).path("id").asLong()).orElseThrow();
        assertThat(storage.original(video.getStorageId())).startsWith(root);
        assertThat(storage.original(video.getStorageId()).getFileName().toString()).isEqualTo("original.mp4");
    }

    @Test
    void enforcesSizeLimitIncludingWhenDeclaredSizeIsWrong() throws Exception {
        mvc.perform(multipart(base()).file(file("clip.mp4", new byte[100001])))
                .andExpect(status().is(413));
        assertThatThrownBy(() -> service.create(partidoId, "clip.mp4", 1, new ByteArrayInputStream(new byte[100001])))
                .isInstanceOf(VideoTooLargeException.class);
        assertEmpty();
    }

    @Test
    void missingInspectionToolReturns503AndCleansStagedFile() throws Exception {
        String original = properties.getFfprobe();
        try {
            properties.setFfprobe("/no-such-semillerotool/ffprobe");
            mvc.perform(multipart(base()).file(file("clip.mp4", valid))).andExpect(status().isServiceUnavailable());
            assertEmpty();
        } finally {
            properties.setFfprobe(original);
        }
    }

    @Test
    void persistenceFailureCleansConfirmedOriginal() throws Exception {
        doThrow(new DataIntegrityViolationException("simulated constraint"))
                .when(videos).saveAndFlush(any(VideoPartido.class));
        mvc.perform(multipart(base()).file(file("clip.mp4", valid))).andExpect(status().isConflict());
        assertEmpty();
    }

    @Test
    void youtubeFailureKeepsOriginalAndRetryReusesSameReceiptAndTitle() throws Exception {
        long id = upload().path("id").asLong();
        when(publisher.publish(any())).thenThrow(new IllegalStateException("secret OAuth details"))
                .thenReturn(new YouTubePublisher.Result(EstadoYoutube.DISPONIBLE, "videoABC123"));
        assertThat(worker.processNext()).isTrue();
        mvc.perform(get(base() + "/" + id)).andExpect(jsonPath("$.estadoYoutube").value("ERROR"))
                .andExpect(jsonPath("$.disponibleParaAnalisis").value(true))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret OAuth"))));
        mvc.perform(post(base() + "/" + id + "/youtube/reintentar")).andExpect(status().isAccepted());
        mvc.perform(post(base() + "/" + id + "/youtube/reintentar")).andExpect(status().isConflict());
        assertThat(worker.processNext()).isTrue();
        var captor = org.mockito.ArgumentCaptor.forClass(YouTubePublisher.Publication.class);
        verify(publisher, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues().get(0)).isEqualTo(captor.getAllValues().get(1));
        assertThat(captor.getValue().title()).isEqualTo("2026-05-02");
        assertThat(videos.count()).isEqualTo(1);
        assertThat(analyses.count()).isEqualTo(1);
        assertThat(analyses.findAll().getFirst().getRespuestaJson()).isEqualTo(PAYLOAD);
        mvc.perform(get(base() + "/" + id)).andExpect(jsonPath("$.estadoYoutube").value("DISPONIBLE"))
                .andExpect(jsonPath("$.youtubeUrl").value("https://www.youtube.com/watch?v=videoABC123"));
    }

    @Test
    void processingIsPolledUntilAvailableWithoutNewUploadRecord() throws Exception {
        long id = upload().path("id").asLong();
        when(publisher.publish(any())).thenReturn(new YouTubePublisher.Result(EstadoYoutube.PROCESANDO, "videoABC123"))
                .thenReturn(new YouTubePublisher.Result(EstadoYoutube.DISPONIBLE, "videoABC123"));
        worker.processNext();
        assertThat(videos.findById(id).orElseThrow().getEstadoYoutube()).isEqualTo(EstadoYoutube.PROCESANDO);
        assertThat(worker.processNext()).isFalse();
        var video = videos.findById(id).orElseThrow();
        video.setProximoIntento(Instant.now().minusSeconds(1));
        videos.saveAndFlush(video);
        worker.processNext();
        assertThat(videos.findById(id).orElseThrow().getEstadoYoutube()).isEqualTo(EstadoYoutube.DISPONIBLE);
        assertThat(worker.processNext()).isFalse();
    }

    @Test
    void expiredClaimIsRecoverableAfterRestart() throws Exception {
        long id = upload().path("id").asLong();
        var video = videos.findById(id).orElseThrow();
        video.setEstadoYoutube(EstadoYoutube.SUBIENDO);
        video.setClaimToken(UUID.randomUUID().toString());
        video.setProximoIntento(Instant.now().minusSeconds(1));
        videos.saveAndFlush(video);
        when(publisher.publish(any())).thenReturn(new YouTubePublisher.Result(EstadoYoutube.DISPONIBLE, "videoABC123"));
        assertThat(worker.processNext()).isTrue();
        assertThat(videos.findById(id).orElseThrow().getClaimToken()).isNull();
    }

    @Test
    void expiredSessionRequiresReviewAndCannotBlindlyRetry() throws Exception {
        long id = upload().path("id").asLong();
        when(publisher.publish(any())).thenThrow(new YouTubePublicationException(true));
        worker.processNext();
        mvc.perform(post(base() + "/" + id + "/youtube/reintentar")).andExpect(status().isConflict());
        assertThat(videos.findById(id).orElseThrow().getEstadoYoutube()).isEqualTo(EstadoYoutube.REVISION_REQUERIDA);
    }

    @Test
    void disabledYoutubeStillAcceptsAndPreservesTheOriginal() throws Exception {
        properties.getYoutube().setEnabled(false);
        try {
            long id = upload().path("id").asLong();
            mvc.perform(get(base() + "/" + id)).andExpect(jsonPath("$.estadoYoutube").value("DESHABILITADO"))
                    .andExpect(jsonPath("$.disponibleParaAnalisis").value(true));
            assertThat(worker.processNext()).isFalse();
            verifyNoInteractions(publisher);
        } finally {
            properties.getYoutube().setEnabled(true);
        }
    }

    @Test
    void concurrentPollersCannotPublishTheSameClaim() throws Exception {
        upload();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(publisher.publish(any())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
            return new YouTubePublisher.Result(EstadoYoutube.DISPONIBLE, "videoABC123");
        });
        var completion = new CompletableFuture<Boolean>();
        Thread.startVirtualThread(() -> {
            try { completion.complete(worker.processNext()); }
            catch (Throwable error) { completion.completeExceptionally(error); }
        });
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(worker.processNext()).isFalse();
        } finally {
            release.countDown();
        }
        assertThat(completion.get(10, TimeUnit.SECONDS)).isTrue();
        verify(publisher, times(1)).publish(any());
    }

    @Test
    void pendingInferenceKeepsUploadUnacceptedAndYoutubeCannotPublishIt() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CompletableFuture<String> pending = new CompletableFuture<>();
        when(gateway.evaluate(any())).thenAnswer(call -> {
            AnalysisGateway.Submission submission = call.getArgument(0);
            assertThat(submission.original().getFileName().toString()).isEqualTo("upload.part");
            assertThat(Files.readAllBytes(submission.original())).isEqualTo(valid);
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
                    .as("inference runs outside SQL transaction").isFalse();
            assertThatCode(() -> UUID.fromString(submission.correlationId())).doesNotThrowAnyException();
            entered.countDown();
            return pending;
        });
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var upload = executor.submit(() -> mvc.perform(multipart(base()).file(file("partido.mp4", valid))).andReturn());
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(upload.isDone()).isFalse();
                assertThat(videos.count()).isZero();
                assertThat(analyses.count()).isZero();
                assertThat(worker.processNext()).isFalse();
                assertThat(analysisWorker.processNext()).isFalse();
                verifyNoInteractions(publisher);
                try (var files = Files.walk(root)) {
                    assertThat(files.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).toList())
                            .containsExactly("upload.part");
                }
            } finally { pending.complete(PAYLOAD); }
            assertThat(upload.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(201);
        }
        assertThat(videos.count()).isEqualTo(1);
        assertThat(analyses.count()).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"null", "42", "{broken", "[] trailing"})
    void invalidAnalysisPayloadRejectsUploadAndCleansFiles(String payload) throws Exception {
        when(gateway.evaluate(any())).thenReturn(CompletableFuture.completedFuture(payload));
        mvc.perform(multipart(base()).file(file("clip.mp4", valid))).andExpect(status().isBadGateway());
        assertEmpty();
        // The failed request must release its concurrency permit.
        when(gateway.evaluate(any())).thenReturn(CompletableFuture.completedFuture(PAYLOAD));
        upload();
    }

    @Test
    void exceptionalOrCancelledInferenceRejectsUploadWithoutLeakingRemoteDetails() throws Exception {
        when(gateway.evaluate(any())).thenReturn(CompletableFuture.failedFuture(
                new IllegalStateException("secret OAuth details from Python")));
        mvc.perform(multipart(base()).file(file("clip.mp4", valid))).andExpect(status().isBadGateway())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret OAuth"))));
        assertEmpty();
        CompletableFuture<String> cancelled = new CompletableFuture<>(); cancelled.cancel(true);
        when(gateway.evaluate(any())).thenReturn(cancelled);
        mvc.perform(multipart(base()).file(file("clip.mp4", valid))).andExpect(status().isBadGateway());
        assertEmpty();
        when(gateway.evaluate(any())).thenReturn(CompletableFuture.completedFuture(PAYLOAD));
        upload();
    }

    @Test
    void publicWaitDeadlineCancelsFutureAndCleansFile() throws Exception {
        analysisProperties.setRequestTimeout(java.time.Duration.ofMillis(150));
        CompletableFuture<String> pending = new CompletableFuture<>();
        when(gateway.evaluate(any())).thenReturn(pending);
        mvc.perform(multipart(base()).file(file("clip.mp4", valid))).andExpect(status().isGatewayTimeout());
        assertThat(pending.isCancelled()).isTrue();
        assertEmpty();
        analysisProperties.setRequestTimeout(java.time.Duration.ofSeconds(10));
        when(gateway.evaluate(any())).thenReturn(CompletableFuture.completedFuture(PAYLOAD));
        upload();
    }

    @Test
    void gatewayTimeoutIsReportedAs504AndDoesNotPersistAnything() throws Exception {
        when(gateway.evaluate(any())).thenReturn(CompletableFuture.failedFuture(new java.net.http.HttpTimeoutException("secret")));
        mvc.perform(multipart(base()).file(file("clip.mp4", valid))).andExpect(status().isGatewayTimeout());
        assertEmpty();
    }

    @Test
    void disabledAnalysisRejectsBeforeStagingOrInvokingPython() throws Exception {
        analysisProperties.setEnabled(false);
        mvc.perform(multipart(base()).file(file("clip.mp4", valid))).andExpect(status().isServiceUnavailable());
        assertEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    void unconfiguredAnalysisRejectsBeforeWritingOrSendingVideo() throws Exception {
        var previous = analysisProperties.getBaseUrl();
        try {
            analysisProperties.setBaseUrl(null);
            mvc.perform(multipart(base()).file(file("clip.mp4", valid))).andExpect(status().isServiceUnavailable());
            assertEmpty();
            verifyNoInteractions(gateway);
        } finally { analysisProperties.setBaseUrl(previous); }
    }

    @Test
    void originalCommitFailureDoesNotLeaveAcceptedVideoOrAnalysis() throws Exception {
        when(gateway.evaluate(any())).thenAnswer(call -> {
            AnalysisGateway.Submission submission = call.getArgument(0);
            Files.delete(submission.original());
            return CompletableFuture.completedFuture(PAYLOAD);
        });
        mvc.perform(multipart(base()).file(file("clip.mp4", valid))).andExpect(status().isServiceUnavailable());
        assertEmpty();
        doReturn(CompletableFuture.completedFuture(PAYLOAD)).when(gateway).evaluate(any());
        upload();
    }

    @Test
    void unavailableCapacityRejectsUploadAndIsSharedWithLegacyAnalysisWorker() throws Exception {
        VideoPartido legacy = legacyVideo();
        long legacyAnalysis = analysisService.create(partidoId, legacy.getId()).id();
        CompletableFuture<String> pending = new CompletableFuture<>();
        when(gateway.evaluate(any())).thenReturn(pending);
        assertThat(analysisWorker.processNext()).isTrue();
        mvc.perform(multipart(base()).file(file("clip.mp4", valid))).andExpect(status().isServiceUnavailable());
        assertThat(videos.count()).isEqualTo(1);
        assertThat(analyses.count()).isEqualTo(1);
        verify(gateway, times(1)).evaluate(any());
        pending.complete("[]");
        await(() -> analyses.findById(legacyAnalysis).orElseThrow().getEstado().isTerminal());
        // Drain settlement so release is observed even when the completion thread just committed.
        analysisWorker.processNext();
        when(gateway.evaluate(any())).thenReturn(CompletableFuture.completedFuture(PAYLOAD));
        upload();
        assertThat(videos.count()).isEqualTo(2);
        assertThat(analyses.count()).isEqualTo(2);
    }

    @Test
    void pendingUploadPreventsLegacyWorkerFromExceedingSharedCapacity() throws Exception {
        VideoPartido legacy = legacyVideo();
        long legacyAnalysis = analysisService.create(partidoId, legacy.getId()).id();
        CountDownLatch entered = new CountDownLatch(1);
        CompletableFuture<String> pending = new CompletableFuture<>();
        when(gateway.evaluate(any())).thenAnswer(call -> { entered.countDown(); return pending; });
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var upload = executor.submit(() -> service.create(partidoId, "partido.mp4", valid.length,
                    new ByteArrayInputStream(valid)));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(analysisWorker.processNext()).isFalse();
                assertThat(analyses.findById(legacyAnalysis).orElseThrow().getEstado()).isEqualTo(EstadoAnalisis.PENDIENTE);
                mvc.perform(multipart(base()).file(file("second.mp4", valid))).andExpect(status().isServiceUnavailable());
                verify(gateway, times(1)).evaluate(any());
            } finally { pending.complete(PAYLOAD); }
            upload.get(10, TimeUnit.SECONDS);
        }
        when(gateway.evaluate(any())).thenReturn(CompletableFuture.completedFuture("[]"));
        assertThat(analysisWorker.processNext()).isTrue();
        await(() -> analyses.findById(legacyAnalysis).orElseThrow().getEstado().isTerminal());
        verify(gateway, times(2)).evaluate(any());
    }

    @Test
    void analysisPersistenceFailureRollsBackVideoAndCleansConfirmedOriginal() throws Exception {
        doThrow(new DataIntegrityViolationException("simulated analysis constraint"))
                .when(analyses).saveAndFlush(any(AnalysisJob.class));
        mvc.perform(multipart(base()).file(file("clip.mp4", valid))).andExpect(status().isConflict());
        assertEmpty();
    }

    @Test
    void transactionCommitFailureRollsBackBothRecordsAndCleansConfirmedOriginal() throws Exception {
        doAnswer(call -> {
            AnalysisJob saved = call.getArgument(0);
            entityManager.persist(saved);
            entityManager.flush();
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void beforeCommit(boolean readOnly) {
                            throw new DataIntegrityViolationException("simulated commit failure");
                        }
                    });
            return saved;
        }).when(analyses).saveAndFlush(any(AnalysisJob.class));
        mvc.perform(multipart(base()).file(file("clip.mp4", valid))).andExpect(status().isConflict());
        assertEmpty();
    }

    private VideoPartido legacyVideo() throws Exception {
        VideoPartido video = new VideoPartido();
        video.setPartido(partidos.findById(partidoId).orElseThrow());
        video.setStorageId(UUID.randomUUID().toString());
        video.setNombreOriginal("legacy.mp4"); video.setFormato("mp4");
        video.setTamanoBytes((long) valid.length); video.setSha256("a".repeat(64));
        video.setFechaCarga(Instant.now()); video.setTituloYoutube("2026-05-02");
        video.setEstadoYoutube(EstadoYoutube.DESHABILITADO);
        Path original = storage.original(video.getStorageId());
        Files.createDirectories(original.getParent()); Files.write(original, valid);
        return videos.saveAndFlush(video);
    }

    private void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) { if (condition.getAsBoolean()) return; Thread.sleep(10); }
        assertThat(condition.getAsBoolean()).as("asynchronous settlement").isTrue();
    }

    private JsonNode upload() throws Exception {
        String result = mvc.perform(multipart(base()).file(file("partido.mp4", valid)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(result);
    }

    private MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "video/mp4", bytes);
    }

    private String base() { return "/api/v1/partidos/" + partidoId + "/videos"; }

    private void assertEmpty() throws Exception {
        assertThat(videos.count()).isZero();
        assertThat(analyses.count()).isZero();
        try (var files = Files.list(root)) { assertThat(files.count()).isZero(); }
        verifyNoInteractions(publisher);
    }
}

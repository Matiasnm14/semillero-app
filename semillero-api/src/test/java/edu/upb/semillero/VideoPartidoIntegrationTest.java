package edu.upb.semillero;

import edu.upb.semillero.config.VideoProperties;
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
    private long partidoId;
    private byte[] valid;

    @BeforeEach
    void setup() throws Exception {
        videos.deleteAll();
        partidos.deleteAll();
        clubes.deleteAll();
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                if (!p.equals(root)) Files.delete(p);
            }
        }
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
    void uploadsOriginalAndRecoversMetadataWithoutPublishingInTheRequest() throws Exception {
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
        try (var files = Files.list(root)) { assertThat(files.count()).isZero(); }
        verifyNoInteractions(publisher);
    }
}

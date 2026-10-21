package edu.upb.semillero;

import edu.upb.semillero.config.AnalysisProperties;
import edu.upb.semillero.repository.dto.response.AnalysisJobResponseDto;
import edu.upb.semillero.repository.entity.*;
import edu.upb.semillero.repository.enums.*;
import edu.upb.semillero.repository.repos.*;
import edu.upb.semillero.service.AnalysisJobService;
import edu.upb.semillero.service.analysis.*;
import edu.upb.semillero.service.exception.AnalysisConflictException;
import edu.upb.semillero.service.video.LocalVideoStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static edu.upb.semillero.service.analysis.AnalysisGatewayException.Reason.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:analysis-jobs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "analysis.enabled=true", "analysis.base-url=http://127.0.0.1:9999", "analysis.worker-enabled=false",
        "video.youtube.enabled=false", "video.youtube.worker-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalysisJobIntegrationTest {
    @TempDir static Path root;
    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) { registry.add("video.storage-dir", () -> root.toString()); }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AnalysisJobRepository jobs;
    @Autowired VideoPartidoRepository videos;
    @Autowired PartidoRepository partidos;
    @Autowired ClubRepository clubes;
    @Autowired AnalysisJobService service;
    @Autowired AnalysisWorker worker;
    @Autowired AnalysisProperties properties;
    @Autowired LocalVideoStorage storage;
    @MockitoBean AnalysisGateway gateway;
    @MockitoBean(name = "analysisClock") Clock clock;
    private final Instant now = Instant.parse("2026-10-21T19:00:00Z");
    private long partidoId;
    private VideoPartido video;

    @BeforeEach
    void setup() throws Exception {
        jobs.deleteAll();
        videos.deleteAll();
        partidos.deleteAll();
        clubes.deleteAll();
        when(clock.instant()).thenReturn(now);
        properties.setEnabled(true);
        properties.setMaxFailures(3);
        properties.setMaxDuration(Duration.ofHours(6));
        Club local = new Club();
        local.setNombreClub("Local");
        Club visitante = new Club();
        visitante.setNombreClub("Visitante");
        Partido partido = new Partido();
        partido.setClubLocal(clubes.saveAndFlush(local));
        partido.setClubVisitante(clubes.saveAndFlush(visitante));
        partido.setFechaInicio(LocalDateTime.parse("2026-10-20T10:00:00"));
        partido.setEstado(EstadoPartido.FINALIZADO);
        partidoId = partidos.saveAndFlush(partido).getId();
        video = video(partido);
    }

    @Test
    void acceptsPersistsAndListsPendingJobWithoutCallingPythonOrYoutube() throws Exception {
        var job = create();
        assertThat(job.get("estado").asText()).isEqualTo("PENDIENTE");
        long id = job.get("id").asLong();
        mvc.perform(get("/api/v1/analisis/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.partidoId").value(partidoId)).andExpect(jsonPath("$.videoId").value(video.getId()))
                .andExpect(jsonPath("$.fechaSolicitud").value(now.toString()))
                .andExpect(jsonPath("$.porcentaje").isEmpty()).andExpect(jsonPath("$.fechaFin").isEmpty())
                .andExpect(jsonPath("$.resultadosDisponibles").value(false))
                .andExpect(jsonPath("$.claimToken").doesNotExist()).andExpect(jsonPath("$.storageId").doesNotExist())
                .andExpect(jsonPath("$.resultadoRef").doesNotExist());
        mvc.perform(get(base())).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        assertThat(jobs.count()).isEqualTo(1);
        assertThat(videos.findById(video.getId()).orElseThrow().getEstadoYoutube()).isEqualTo(EstadoYoutube.DESHABILITADO);
        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsMissingResourcesAndCrossMatchRelationships() throws Exception {
        mvc.perform(post("/api/v1/partidos/999999/videos/1/analisis")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/partidos/" + partidoId + "/videos/999999/analisis")).andExpect(status().isNotFound());
        var another = new Partido();
        another.setClubLocal(video.getPartido().getClubLocal());
        another.setClubVisitante(video.getPartido().getClubVisitante());
        another.setFechaInicio(LocalDateTime.now());
        another.setEstado(EstadoPartido.FINALIZADO);
        long otherId = partidos.saveAndFlush(another).getId();
        String crossed = "/api/v1/partidos/" + otherId + "/videos/" + video.getId() + "/analisis";
        mvc.perform(post(crossed)).andExpect(status().isNotFound());
        mvc.perform(get(crossed)).andExpect(status().isNotFound());
        assertThat(jobs.count()).isZero();
    }

    @Test
    void rejectsMissingOriginalAndDisabledAnalysisWithoutCreatingJobs() throws Exception {
        properties.setEnabled(false);
        mvc.perform(post(base())).andExpect(status().isServiceUnavailable());
        properties.setEnabled(true);
        Files.delete(storage.original(video.getStorageId()));
        mvc.perform(post(base())).andExpect(status().isServiceUnavailable());
        assertThat(jobs.count()).isZero();
        verifyNoInteractions(gateway);
    }

    @Test
    void returns404ForUnknownAnalysisAnd400ForMalformedIdentifiers() throws Exception {
        mvc.perform(get("/api/v1/analisis/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/analisis/-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/analisis/abc")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/partidos/-1/videos/1/analisis")).andExpect(status().isBadRequest());
    }

    @Test
    void conflictPointsToExistingAnalysisAndCompletedWorkRequiresExplicitReprocessing() throws Exception {
        long id = create().get("id").asLong();
        mvc.perform(post(base())).andExpect(status().isConflict()).andExpect(jsonPath("$.analysisId").value(id))
                .andExpect(jsonPath("$.analysisUrl").value("/api/v1/analisis/" + id));
        when(gateway.submit(any())).thenReturn(snapshot(id, 1, EstadoAnalisis.COMPLETADO, null, false, null));
        worker.processNext();
        mvc.perform(post(base())).andExpect(status().isConflict()).andExpect(jsonPath("$.analysisId").value(id));
        assertThat(jobs.count()).isEqualTo(1);
    }

    @Test
    void concurrentRequestsProduceOneJobButDifferentVideosCanRunIndependently() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Object> request = () -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                try { return service.create(partidoId, video.getId()); }
                catch (AnalysisConflictException e) { return e; }
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .filteredOn(AnalysisJobResponseDto.class::isInstance).hasSize(1);
        }
        assertThat(jobs.count()).isEqualTo(1);
        var other = video(video.getPartido());
        assertThat(service.create(partidoId, other.getId()).estado()).isEqualTo(EstadoAnalisis.PENDIENTE);
        assertThat(jobs.count()).isEqualTo(2);
    }

    @Test
    void continuesWithoutClientRequestsAndReportsProgressAndConfirmedResults() throws Exception {
        long id = create().get("id").asLong();
        when(gateway.submit(any())).thenReturn(snapshot(id, 0, EstadoAnalisis.PENDIENTE, null, false, null));
        assertThat(worker.processNext()).isTrue();
        assertThat(job(id).getEstado()).isEqualTo(EstadoAnalisis.PENDIENTE);
        when(clock.instant()).thenReturn(now.plusSeconds(5));
        when(gateway.getStatus(id)).thenReturn(new AnalysisGateway.Snapshot(id, 1L, EstadoAnalisis.PROCESANDO,
                35.0, 2100L, 6000L, now, null, false, null, null));
        assertThat(worker.processNext()).isTrue();
        mvc.perform(get("/api/v1/analisis/" + id)).andExpect(jsonPath("$.estado").value("PROCESANDO"))
                .andExpect(jsonPath("$.porcentaje").value(35.0)).andExpect(jsonPath("$.fotogramasProcesados").value(2100));
        when(clock.instant()).thenReturn(now.plusSeconds(10));
        when(gateway.getStatus(id)).thenReturn(snapshot(id, 2, EstadoAnalisis.COMPLETADO, 100.0, true, "resultados:101"));
        worker.processNext();
        mvc.perform(get("/api/v1/analisis/" + id)).andExpect(jsonPath("$.estado").value("COMPLETADO"))
                .andExpect(jsonPath("$.fechaFin").value(now.plusSeconds(1).toString()))
                .andExpect(jsonPath("$.resultadosDisponibles").value(true));
        assertThat(worker.processNext()).isFalse();
        assertThat(job(id).getClaimToken()).isNull();
        verify(gateway, times(1)).submit(any());
        verify(gateway, times(2)).getStatus(id);
    }

    @Test
    void completionCanHonestlyReportResultsNotYetAvailable() throws Exception {
        long id = create().get("id").asLong();
        when(gateway.submit(any())).thenReturn(snapshot(id, 1, EstadoAnalisis.COMPLETADO, null, false, null));
        worker.processNext();
        mvc.perform(get("/api/v1/analisis/" + id)).andExpect(jsonPath("$.estado").value("COMPLETADO"))
                .andExpect(jsonPath("$.fechaFin").exists()).andExpect(jsonPath("$.resultadosDisponibles").value(false))
                .andExpect(jsonPath("$.porcentaje").isEmpty());
    }

    @Test
    void recoversExpiredReservationWithSameIdAndRejectsConcurrentPoller() throws Exception {
        long id = create().get("id").asLong();
        var saved = job(id);
        saved.setClaimToken(UUID.randomUUID().toString());
        saved.setLeaseUntil(now.minusSeconds(1));
        jobs.saveAndFlush(saved);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(gateway.submit(any())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
            return snapshot(id, 1, EstadoAnalisis.PROCESANDO, null, false, null);
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(worker::processNext);
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(worker.processNext()).isFalse();
                assertThat(job(id).getEstado()).isEqualTo(EstadoAnalisis.PENDIENTE);
                mvc.perform(get("/api/v1/clubes")).andExpect(status().isOk());
            } finally { release.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(job(id).getEstado()).isEqualTo(EstadoAnalisis.PROCESANDO);
        verify(gateway).submit(argThat(value -> value.analysisId().equals(id) && value.storageId().equals(video.getStorageId())));
    }

    @Test
    void responseFromExpiredOwnerCannotOverwriteNewReservation() throws Exception {
        long id = create().get("id").asLong();
        when(gateway.submit(any())).thenAnswer(call -> {
            var claimed = job(id);
            claimed.setClaimToken("replacement-token");
            jobs.saveAndFlush(claimed);
            return snapshot(id, 1, EstadoAnalisis.COMPLETADO, null, false, null);
        });
        worker.processNext();
        assertThat(job(id).getEstado()).isEqualTo(EstadoAnalisis.PENDIENTE);
        assertThat(job(id).getClaimToken()).isEqualTo("replacement-token");
    }

    @Test
    void lostSubmissionResponseRetriesSameIdentityAndEventuallySurfacesSafeError() throws Exception {
        long id = create().get("id").asLong();
        when(gateway.submit(any())).thenThrow(new AnalysisGatewayException(TRANSPORT));
        worker.processNext();
        assertThat(job(id).getEstado()).isEqualTo(EstadoAnalisis.PENDIENTE);
        assertThat(worker.processNext()).isFalse();
        mvc.perform(get("/api/v1/analisis/" + id)).andExpect(status().isOk());
        when(clock.instant()).thenReturn(now.plusSeconds(5));
        worker.processNext();
        when(clock.instant()).thenReturn(now.plusSeconds(15));
        worker.processNext();
        assertThat(job(id).getEstado()).isEqualTo(EstadoAnalisis.ERROR);
        assertThat(job(id).getCodigoError()).isEqualTo("RECUPERACION_AGOTADA");
        assertThat(job(id).getFechaFin()).isEqualTo(now.plusSeconds(15));
        assertThat(jobs.count()).isEqualTo(1);
        verify(gateway, times(3)).submit(argThat(value -> value.analysisId().equals(id)));
    }

    @Test
    void missingOriginalAndExceededDeadlineFinishWithoutCallingRemoteService() throws Exception {
        long id = create().get("id").asLong();
        Files.delete(storage.original(video.getStorageId()));
        worker.processNext();
        assertThat(job(id).getCodigoError()).isEqualTo("ORIGINAL_NO_DISPONIBLE");
        var another = video(video.getPartido());
        long otherId = service.create(partidoId, another.getId()).id();
        when(clock.instant()).thenReturn(now.plus(Duration.ofHours(7)));
        worker.processNext();
        assertThat(job(otherId).getCodigoError()).isEqualTo("TIEMPO_ANALISIS_AGOTADO");
        verifyNoInteractions(gateway);
    }

    @Test
    void remoteFailureIsSanitizedAndUnknownExceptionsNeverExposeDetails() throws Exception {
        long id = create().get("id").asLong();
        when(gateway.submit(any())).thenReturn(new AnalysisGateway.Snapshot(id, 1L, EstadoAnalisis.ERROR,
                null, null, null, null, now, false, null, "secret path and token"));
        worker.processNext();
        mvc.perform(get("/api/v1/analisis/" + id)).andExpect(jsonPath("$.estado").value("ERROR"))
                .andExpect(jsonPath("$.codigoError").value("ANALISIS_FALLIDO"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        long otherId = service.create(partidoId, video(video.getPartido()).getId()).id();
        properties.setMaxFailures(1);
        when(gateway.submit(any())).thenThrow(new IllegalStateException("secret credentials"));
        worker.processNext();
        assertThat(job(otherId).getMensajeError()).doesNotContain("secret");
        assertThat(job(otherId).getEstado()).isEqualTo(EstadoAnalisis.ERROR);
    }

    @Test
    void staleRemoteRevisionDoesNotRegressProgress() throws Exception {
        long id = create().get("id").asLong();
        when(gateway.submit(any())).thenReturn(snapshot(id, 2, EstadoAnalisis.PROCESANDO, 50.0, false, null));
        worker.processNext();
        when(clock.instant()).thenReturn(now.plusSeconds(5));
        when(gateway.getStatus(id)).thenReturn(snapshot(id, 1, EstadoAnalisis.PENDIENTE, null, false, null));
        worker.processNext();
        assertThat(job(id).getEstado()).isEqualTo(EstadoAnalisis.PROCESANDO);
        assertThat(job(id).getPorcentaje()).isEqualTo(50.0);
        assertThat(job(id).getFechaActualizacion()).isEqualTo(now);
    }

    @Test
    void invalidRemoteProgressIsRejectedInsteadOfStored() throws Exception {
        long id = create().get("id").asLong();
        when(gateway.submit(any())).thenReturn(snapshot(id, 1, EstadoAnalisis.PROCESANDO, 101.0, false, null));
        worker.processNext();
        assertThat(job(id).getEstado()).isEqualTo(EstadoAnalisis.ERROR);
        assertThat(job(id).getPorcentaje()).isNull();
        assertThat(job(id).getCodigoError()).isEqualTo("RESPUESTA_ANALIZADOR_INVALIDA");
    }

    private tools.jackson.databind.JsonNode create() throws Exception {
        var response = mvc.perform(post(base())).andExpect(status().isAccepted())
                .andExpect(header().exists("Location")).andReturn().getResponse();
        var body = json.readTree(response.getContentAsString());
        assertThat(response.getHeader("Location")).endsWith("/api/v1/analisis/" + body.path("id").asLong());
        return body;
    }

    private String base() { return "/api/v1/partidos/" + partidoId + "/videos/" + video.getId() + "/analisis"; }
    private AnalysisJob job(long id) { return jobs.findById(id).orElseThrow(); }

    private AnalysisGateway.Snapshot snapshot(long id, long revision, EstadoAnalisis state, Double percentage,
            boolean available, String ref) {
        return new AnalysisGateway.Snapshot(id, revision, state, percentage, null, null,
                state == EstadoAnalisis.PENDIENTE ? null : now,
                state.isTerminal() ? now.plusSeconds(1) : null, available, ref, null);
    }

    private VideoPartido video(Partido partido) throws Exception {
        var value = new VideoPartido();
        value.setPartido(partido);
        value.setStorageId(UUID.randomUUID().toString());
        value.setNombreOriginal("partido.mp4");
        value.setFormato("mp4");
        value.setTamanoBytes(1L);
        value.setSha256("a".repeat(64));
        value.setFechaCarga(now);
        value.setTituloYoutube("2026-10-20");
        value.setEstadoYoutube(EstadoYoutube.DESHABILITADO);
        Path original = storage.original(value.getStorageId());
        Files.createDirectories(original.getParent());
        Files.write(original, new byte[]{1});
        return videos.saveAndFlush(value);
    }
}

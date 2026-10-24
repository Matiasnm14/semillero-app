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
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean AnalysisJobRepository jobs;
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
        properties.setMaxConcurrentJobs(2);
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
        when(gateway.evaluate(any())).thenReturn(CompletableFuture.completedFuture("[]"));
        worker.processNext();
        await(() -> job(id).getEstado().isTerminal());
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
    void futureKeepsJobProcessingAndPublicApiResponsiveThenStoresUnmappedJson() throws Exception {
        long id = create().get("id").asLong();
        CompletableFuture<String> future = new CompletableFuture<>();
        when(gateway.evaluate(any())).thenReturn(future);
        assertThat(worker.processNext()).isTrue();
        assertThat(future.isDone()).isFalse();
        mvc.perform(get("/api/v1/analisis/" + id)).andExpect(jsonPath("$.estado").value("PROCESANDO"))
                .andExpect(jsonPath("$.fechaInicio").exists()).andExpect(jsonPath("$.porcentaje").isEmpty())
                .andExpect(jsonPath("$.respuestaRecibida").value(false));
        mvc.perform(get("/api/v1/clubes")).andExpect(status().isOk());
        String payload = "[{\"dorsal\":9,\"distancia\":12.123456789}]";
        future.complete(payload);
        await(() -> job(id).getEstado().isTerminal());
        assertThat(job(id).getRespuestaJson()).isEqualTo(payload);
        mvc.perform(get("/api/v1/analisis/" + id)).andExpect(jsonPath("$.estado").value("COMPLETADO"))
                .andExpect(jsonPath("$.respuestaRecibida").value(true))
                .andExpect(jsonPath("$.resultadosDisponibles").value(false))
                .andExpect(jsonPath("$.respuestaJson").doesNotExist()).andExpect(jsonPath("$.porcentaje").isEmpty());
        assertThat(contextMetricsCount()).isZero();
        assertThat(worker.processNext()).isFalse();
        verify(gateway, times(1)).evaluate(argThat(value -> value.correlationId().equals(Long.toString(id))
                && value.original().equals(storage.original(video.getStorageId()))));
    }

    @Test
    void limitsConcurrentFuturesAndStartsQueuedWorkWhenCapacityReturns() throws Exception {
        properties.setMaxConcurrentJobs(1);
        long first = create().get("id").asLong();
        long second = service.create(partidoId, video(video.getPartido()).getId()).id();
        CompletableFuture<String> pending = new CompletableFuture<>();
        when(gateway.evaluate(any())).thenReturn(pending).thenReturn(CompletableFuture.completedFuture("{}"));
        assertThat(worker.processNext()).isTrue();
        assertThat(worker.processNext()).isFalse();
        assertThat(job(second).getEstado()).isEqualTo(EstadoAnalisis.PENDIENTE);
        pending.complete("[]");
        await(() -> job(first).getEstado().isTerminal());
        assertThat(worker.processNext()).isTrue();
        await(() -> job(second).getEstado().isTerminal());
        verify(gateway, times(2)).evaluate(any());
    }

    @Test
    void renewsLeaseWhileFutureIsPendingWithoutPollingPython() throws Exception {
        long id = create().get("id").asLong();
        CompletableFuture<String> future = new CompletableFuture<>();
        when(gateway.evaluate(any())).thenReturn(future);
        worker.processNext();
        for (int seconds : new int[]{20, 40, 60}) {
            when(clock.instant()).thenReturn(now.plusSeconds(seconds));
            worker.processNext();
            assertThat(job(id).getLeaseUntil()).isAfter(now.plusSeconds(seconds));
        }
        future.complete("[]");
        await(() -> job(id).getEstado().isTerminal());
        assertThat(job(id).getEstado()).isEqualTo(EstadoAnalisis.COMPLETADO);
        verify(gateway, times(1)).evaluate(any());
    }

    @Test
    void futuresCompletingOutOfOrderKeepTheirOriginalAnalysisContext() throws Exception {
        long first = create().get("id").asLong();
        long second = service.create(partidoId, video(video.getPartido()).getId()).id();
        CompletableFuture<String> a = new CompletableFuture<>(), b = new CompletableFuture<>();
        when(gateway.evaluate(any())).thenAnswer(call ->
                ((AnalysisGateway.Submission) call.getArgument(0)).correlationId().equals(Long.toString(first)) ? a : b);
        assertThat(worker.processNext()).isTrue();
        assertThat(worker.processNext()).isTrue();
        b.complete("[{\"dorsal\":9,\"distancia\":22}]");
        await(() -> job(second).getEstado().isTerminal());
        assertThat(job(first).getEstado()).isEqualTo(EstadoAnalisis.PROCESANDO);
        assertThat(job(first).getRespuestaJson()).isNull();
        a.complete("[{\"dorsal\":9,\"distancia\":11}]");
        await(() -> job(first).getEstado().isTerminal());
        assertThat(job(first).getRespuestaJson()).contains("11");
        assertThat(job(second).getRespuestaJson()).contains("22");
    }

    @Test
    void expiredForeignReservationBecomesErrorWithoutResendingUncertainEvaluation() throws Exception {
        long id = create().get("id").asLong();
        var saved = job(id);
        saved.setEstado(EstadoAnalisis.PROCESANDO); saved.setFechaInicio(now.minusSeconds(5));
        saved.setEnviado(true); saved.setClaimToken("previous-process"); saved.setLeaseUntil(now.minusSeconds(1));
        jobs.saveAndFlush(saved);
        worker.processNext();
        assertThat(job(id).getEstado()).isEqualTo(EstadoAnalisis.ERROR);
        assertThat(job(id).getCodigoError()).isEqualTo("EVALUACION_INTERRUMPIDA");
        verifyNoInteractions(gateway);
    }

    @Test
    void lateFutureCannotOverwriteReplacementToken() throws Exception {
        long id = create().get("id").asLong();
        CompletableFuture<String> future = new CompletableFuture<>();
        when(gateway.evaluate(any())).thenReturn(future);
        worker.processNext();
        var changed = job(id); changed.setClaimToken("replacement-token"); jobs.saveAndFlush(changed);
        future.complete("[]");
        // Drain completed Futures deterministically; ownership must also hold in the SQL transaction.
        worker.processNext();
        assertThat(job(id).getEstado()).isEqualTo(EstadoAnalisis.PROCESANDO);
        assertThat(job(id).getClaimToken()).isEqualTo("replacement-token");
        assertThat(job(id).getRespuestaJson()).isNull();
    }

    @Test
    void futureAfterLeaseExpirationIsRejected() throws Exception {
        long id = create().get("id").asLong();
        CompletableFuture<String> future = new CompletableFuture<>();
        when(gateway.evaluate(any())).thenReturn(future);
        worker.processNext();
        when(clock.instant()).thenReturn(now.plusSeconds(31));
        future.complete("[]");
        await(() -> job(id).getEstado().isTerminal());
        assertThat(job(id).getEstado()).isEqualTo(EstadoAnalisis.ERROR);
        assertThat(job(id).getRespuestaJson()).isNull();
    }

    @Test
    void timeoutAndTransportErrorsAreTerminalWithoutAutomaticResubmission() throws Exception {
        long id = create().get("id").asLong();
        when(gateway.evaluate(any())).thenReturn(CompletableFuture.failedFuture(new TimeoutException("remote secret")));
        worker.processNext();
        await(() -> job(id).getEstado().isTerminal());
        assertThat(job(id).getCodigoError()).isEqualTo("ESPERA_EVALUACION_AGOTADA");
        long second = service.create(partidoId, video(video.getPartido()).getId()).id();
        when(gateway.evaluate(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("secret credentials")));
        worker.processNext();
        await(() -> job(second).getEstado().isTerminal());
        assertThat(job(second).getMensajeError()).doesNotContain("secret");
        assertThat(worker.processNext()).isFalse();
        verify(gateway, times(2)).evaluate(any());
    }

    @Test
    void malformedFinalPayloadIsNotStoredOrPublished() throws Exception {
        long id = create().get("id").asLong();
        when(gateway.evaluate(any())).thenReturn(CompletableFuture.completedFuture("{not JSON}"));
        worker.processNext();
        await(() -> job(id).getEstado().isTerminal());
        assertThat(job(id).getCodigoError()).isEqualTo("RESPUESTA_ANALIZADOR_INVALIDA");
        assertThat(job(id).getRespuestaJson()).isNull();
        assertThat(job(id).isResultadosDisponibles()).isFalse();
    }

    @Test
    void sqlCommitFailureRetriesSavingTheCompletedFutureWithoutCallingPythonAgain() throws Exception {
        long id = create().get("id").asLong();
        CompletableFuture<String> future = new CompletableFuture<>();
        when(gateway.evaluate(any())).thenReturn(future);
        worker.processNext();
        CountDownLatch attempted = new CountDownLatch(1);
        doAnswer(call -> { attempted.countDown(); throw new org.springframework.dao.DataAccessResourceFailureException("test failure"); })
                .when(jobs).saveAndFlush(any(AnalysisJob.class));
        future.complete("[]");
        assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
        reset(jobs);
        await(() -> { worker.processNext(); return job(id).getEstado().isTerminal(); });
        assertThat(job(id).getEstado()).isEqualTo(EstadoAnalisis.COMPLETADO);
        assertThat(job(id).getRespuestaJson()).isEqualTo("[]");
        verify(gateway, times(1)).evaluate(any());
    }

    @Test
    void missingOriginalAndExceededDeadlineNeverCallPython() throws Exception {
        long id = create().get("id").asLong();
        Files.delete(storage.original(video.getStorageId()));
        worker.processNext();
        assertThat(job(id).getCodigoError()).isEqualTo("ORIGINAL_NO_DISPONIBLE");
        long other = service.create(partidoId, video(video.getPartido()).getId()).id();
        when(clock.instant()).thenReturn(now.plus(Duration.ofHours(7)));
        worker.processNext();
        assertThat(job(other).getCodigoError()).isEqualTo("TIEMPO_ANALISIS_AGOTADO");
        verifyNoInteractions(gateway);
    }

    @Test
    void overallDeadlineCancelsPendingFuture() throws Exception {
        long id = create().get("id").asLong();
        CompletableFuture<String> future = new CompletableFuture<>();
        when(gateway.evaluate(any())).thenReturn(future);
        worker.processNext();
        when(clock.instant()).thenReturn(now.plus(Duration.ofHours(7)));
        worker.processNext();
        await(() -> job(id).getEstado().isTerminal());
        assertThat(future.isCompletedExceptionally()).isTrue();
        assertThat(job(id).getCodigoError()).isEqualTo("TIEMPO_ANALISIS_AGOTADO");
    }

    private long contextMetricsCount() {
        return jdbc.queryForObject("select count(*) from player_match_metrics", Long.class);
    }

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private void await(java.util.function.BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).as("asynchronous database update").isTrue();
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

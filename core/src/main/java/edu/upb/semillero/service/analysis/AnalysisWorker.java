package edu.upb.semillero.service.analysis;

import edu.upb.semillero.config.AnalysisProperties;
import edu.upb.semillero.repository.entity.AnalysisJob;
import edu.upb.semillero.repository.enums.EstadoAnalisis;
import edu.upb.semillero.repository.repos.AnalysisJobRepository;
import edu.upb.semillero.service.video.LocalVideoStorage;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static edu.upb.semillero.service.analysis.AnalysisGatewayException.Reason.*;

@Component
public class AnalysisWorker {
    private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);
    private static final List<EstadoAnalisis> ACTIVE = List.of(EstadoAnalisis.PENDIENTE, EstadoAnalisis.PROCESANDO);
    private final AnalysisJobRepository jobs;
    private final LocalVideoStorage storage;
    private final AnalysisGateway gateway;
    private final AnalysisResultValidator validator;
    private final AnalysisProperties properties;
    private final AnalysisConcurrencyLimiter concurrency;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final ExecutorService completionExecutor;
    private final ConcurrentHashMap<Long, Waiting> running = new ConcurrentHashMap<>();
    private volatile boolean closing;

    private record Work(Long id, String token, Instant deadline, AnalysisGateway.Submission submission) {}
    private record Waiting(Work work, CompletableFuture<String> future) {}

    public AnalysisWorker(AnalysisJobRepository jobs, LocalVideoStorage storage, AnalysisGateway gateway,
            AnalysisResultValidator validator, AnalysisProperties properties, AnalysisConcurrencyLimiter concurrency,
            @Qualifier("analysisClock") Clock clock, PlatformTransactionManager manager,
            @Qualifier("analysisCompletionExecutor") ExecutorService completionExecutor) {
        this.jobs = jobs;
        this.storage = storage;
        this.gateway = gateway;
        this.validator = validator;
        this.properties = properties;
        this.concurrency = concurrency;
        this.clock = clock;
        this.transaction = new TransactionTemplate(manager);
        this.completionExecutor = completionExecutor;
    }

    @Scheduled(scheduler = "analysisScheduler", fixedDelayString = "${analysis.poll-interval:5s}",
            initialDelayString = "${analysis.poll-interval:5s}")
    public void poll() {
        if (!properties.isWorkerEnabled()) return;
        try { processNext(); }
        catch (RuntimeException e) {
            log.warn("No se pudo actualizar la cola de análisis; se revisará en el siguiente ciclo");
        }
    }

    /** Starts at most one evaluation. Also renews local leases and retries persisting completed Futures. */
    public synchronized boolean processNext() {
        if (closing) return false;
        maintainWaiting();
        if (!properties.isEnabled() || running.size() >= properties.getMaxConcurrentJobs()) return false;
        var permit = concurrency.tryAcquire();
        if (permit == null) return false;
        boolean handedOff = false;
        try {
            for (Long id : jobs.findExecutable(ACTIVE, clock.instant(), PageRequest.of(0, 20))) {
                Work work = transaction.execute(status -> claim(id));
                if (work == null) continue;
                CompletableFuture<String> future;
                try {
                    future = gateway.evaluate(work.submission());
                    if (future == null) future = CompletableFuture.failedFuture(new AnalysisGatewayException(INVALID_RESPONSE));
                } catch (RuntimeException e) {
                    future = CompletableFuture.failedFuture(new AnalysisGatewayException(HttpAnalysisGateway.reason(e)));
                }
                long remaining = Math.max(1, java.time.Duration.between(clock.instant(), work.deadline()).toMillis());
                future.orTimeout(remaining, TimeUnit.MILLISECONDS);
                Waiting waiting = new Waiting(work, future);
                running.put(id, waiting);
                // Release independently of SQL completion: retrying a commit never starts more inference.
                future.whenComplete((value, error) -> permit.close());
                handedOff = true;
                future.whenCompleteAsync((value, error) -> settle(waiting), completionExecutor);
                return true;
            }
            return false;
        } finally {
            // No executable job, a rolled-back claim or a dispatch failure must never consume capacity.
            if (!handedOff) permit.close();
        }
    }

    private Work claim(Long id) {
        var job = jobs.findLockedById(id).orElse(null);
        Instant now = clock.instant();
        if (job == null || job.getEstado().isTerminal() || job.getProximaEjecucion() == null
                || job.getProximaEjecucion().isAfter(now)
                || job.getLeaseUntil() != null && job.getLeaseUntil().isAfter(now)) return null;
        // There is no remote job/status API to reconnect a lost Future. Do not resend uncertain work.
        if (job.isEnviado() || job.getEstado() == EstadoAnalisis.PROCESANDO || job.getClaimToken() != null) {
            finishError(job, INTERRUPTED);
            return null;
        }
        Instant deadline = job.getFechaSolicitud().plus(properties.getMaxDuration());
        if (!now.isBefore(deadline)) { finishError(job, DEADLINE); return null; }
        var video = job.getVideo();
        if (!storage.exists(video.getStorageId())) { finishError(job, MISSING_ORIGINAL); return null; }
        String token = UUID.randomUUID().toString();
        job.setClaimToken(token);
        job.setLeaseUntil(now.plus(properties.getLeaseDuration()));
        job.setEstado(EstadoAnalisis.PROCESANDO);
        job.setFechaInicio(now.truncatedTo(ChronoUnit.MICROS));
        job.setFechaActualizacion(now);
        job.setEnviado(true); // Dispatch intent is durable before any network side effect.
        job.setProximaEjecucion(now.plus(properties.getPollInterval()));
        jobs.saveAndFlush(job);
        return new Work(id, token, deadline, new AnalysisGateway.Submission(id, storage.original(video.getStorageId())));
    }

    private void maintainWaiting() {
        for (Waiting waiting : running.values()) {
            if (waiting.future().isDone()) { settle(waiting); continue; }
            if (!clock.instant().isBefore(waiting.work().deadline())) {
                waiting.future().completeExceptionally(new AnalysisGatewayException(DEADLINE));
                continue;
            }
            boolean renewed = Boolean.TRUE.equals(transaction.execute(status -> {
                var job = jobs.findLockedById(waiting.work().id()).orElse(null);
                Instant now = clock.instant();
                if (!owned(job, waiting.work()) || job.getLeaseUntil() == null || !job.getLeaseUntil().isAfter(now)) return false;
                job.setLeaseUntil(now.plus(properties.getLeaseDuration()));
                job.setProximaEjecucion(now.plus(properties.getPollInterval()));
                jobs.saveAndFlush(job);
                return true;
            }));
            if (!renewed) waiting.future().completeExceptionally(new AnalysisGatewayException(INTERRUPTED));
        }
    }

    private void settle(Waiting waiting) {
        if (!waiting.future().isDone()) return;
        try {
            transaction.executeWithoutResult(status -> {
                var job = jobs.findLockedById(waiting.work().id()).orElse(null);
                if (!owned(job, waiting.work())) return;
                Instant now = clock.instant();
                if (!now.isBefore(waiting.work().deadline())) { finishError(job, DEADLINE); return; }
                if (job.getLeaseUntil() == null || !job.getLeaseUntil().isAfter(now)) { finishError(job, INTERRUPTED); return; }
                String payload;
                try {
                    payload = validator.validate(waiting.future().join()); // Already completed: never waits here.
                } catch (RuntimeException e) {
                    finishError(job, HttpAnalysisGateway.reason(e));
                    return;
                }
                job.setRespuestaJson(payload);
                job.setEstado(EstadoAnalisis.COMPLETADO);
                job.setFechaFin(now.truncatedTo(ChronoUnit.MICROS));
                job.setFechaActualizacion(now);
                // The JSON has arrived; player mapping and publication of final metrics belong to HU12.
                job.setResultadosDisponibles(false);
                job.setResultadoRef(null);
                job.setCodigoError(null);
                job.setMensajeError(null);
                job.setFallosConsecutivos(0);
                job.setProximaEjecucion(null);
                release(job);
                jobs.saveAndFlush(job);
            });
            running.remove(waiting.work().id(), waiting);
        } catch (RuntimeException e) {
            // Keep the completed Future so a later cycle can retry the SQL commit, not the Python request.
            log.warn("No se pudo confirmar la respuesta del análisis {}; se volverá a intentar guardar", waiting.work().id());
        }
    }

    private boolean owned(AnalysisJob job, Work work) {
        return job != null && !job.getEstado().isTerminal() && work.token().equals(job.getClaimToken());
    }

    private void finishError(AnalysisJob job, AnalysisGatewayException.Reason reason) {
        job.setEstado(EstadoAnalisis.ERROR);
        job.setFechaFin(clock.instant().truncatedTo(ChronoUnit.MICROS));
        job.setFechaActualizacion(clock.instant());
        job.setCodigoError(reason.code());
        job.setMensajeError(reason.message());
        job.setResultadosDisponibles(false);
        job.setResultadoRef(null);
        job.setRespuestaJson(null);
        job.setFallosConsecutivos(job.getFallosConsecutivos() + 1);
        job.setProximaEjecucion(null);
        release(job);
        jobs.saveAndFlush(job);
    }

    private void release(AnalysisJob job) {
        job.setClaimToken(null);
        job.setLeaseUntil(null);
    }

    @PreDestroy
    public synchronized void close() {
        closing = true;
        for (Waiting waiting : running.values()) {
            waiting.future().completeExceptionally(new AnalysisGatewayException(INTERRUPTED));
            settle(waiting);
        }
    }
}

package edu.upb.semillero.service.analysis;

import edu.upb.semillero.config.AnalysisProperties;
import edu.upb.semillero.repository.entity.AnalysisJob;
import edu.upb.semillero.repository.enums.EstadoAnalisis;
import edu.upb.semillero.repository.repos.AnalysisJobRepository;
import edu.upb.semillero.service.video.LocalVideoStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
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
    private final AnalysisSnapshotValidator validator;
    private final AnalysisProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    private record Work(Long id, String token, boolean submitted, Instant deadline, AnalysisGateway.Submission submission) {}

    public AnalysisWorker(AnalysisJobRepository jobs, LocalVideoStorage storage, AnalysisGateway gateway,
            AnalysisSnapshotValidator validator, AnalysisProperties properties,
            @Qualifier("analysisClock") Clock clock, PlatformTransactionManager manager) {
        this.jobs = jobs;
        this.storage = storage;
        this.gateway = gateway;
        this.validator = validator;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(manager);
    }

    @Scheduled(scheduler = "analysisScheduler", fixedDelayString = "${analysis.poll-interval:5s}",
            initialDelayString = "${analysis.poll-interval:5s}")
    public void poll() {
        if (!properties.isWorkerEnabled()) return;
        try {
            processNext();
        } catch (RuntimeException e) {
            // Do not log remote bodies, credentials, JDBC details or physical paths.
            log.warn("Se interrumpió el seguimiento de análisis; las reservas vencidas se recuperarán");
        }
    }

    public boolean processNext() {
        if (!properties.isEnabled()) return false;
        for (Long id : jobs.findExecutable(ACTIVE, clock.instant(), PageRequest.of(0, 20))) {
            Work work = transaction.execute(status -> claim(id));
            if (work == null) continue;
            try {
                if (!clock.instant().isBefore(work.deadline())) throw new AnalysisGatewayException(DEADLINE);
                if (!storage.exists(work.submission().storageId())) throw new AnalysisGatewayException(MISSING_ORIGINAL);
                var snapshot = work.submitted() ? gateway.getStatus(work.id()) : gateway.submit(work.submission());
                transaction.executeWithoutResult(status -> completePoll(work, snapshot));
            } catch (AnalysisGatewayException e) {
                transaction.executeWithoutResult(status -> fail(work, e.reason()));
            } catch (RuntimeException e) {
                transaction.executeWithoutResult(status -> fail(work, TRANSPORT));
            }
            return true;
        }
        return false;
    }

    private Work claim(Long id) {
        var job = jobs.findLockedById(id).orElse(null);
        Instant now = clock.instant();
        if (job == null || job.getEstado().isTerminal() || job.getProximaEjecucion() == null
                || job.getProximaEjecucion().isAfter(now)
                || job.getLeaseUntil() != null && job.getLeaseUntil().isAfter(now)) return null;
        String token = UUID.randomUUID().toString();
        job.setClaimToken(token);
        job.setLeaseUntil(now.plus(properties.getLeaseDuration()));
        jobs.saveAndFlush(job);
        var video = job.getVideo();
        return new Work(id, token, job.isEnviado(), job.getFechaSolicitud().plus(properties.getMaxDuration()),
                new AnalysisGateway.Submission(id, video.getId(), video.getPartido().getId(), video.getStorageId(), video.getSha256()));
    }

    private void completePoll(Work work, AnalysisGateway.Snapshot snapshot) {
        var job = owned(work);
        if (job == null) return;
        if (!clock.instant().isBefore(work.deadline())) {
            finishError(job, DEADLINE);
        } else {
            if (validator.isNewer(snapshot, job)) {
                job.setEstado(snapshot.estado());
                job.setRevisionRemota(snapshot.revision());
                job.setFechaInicio(snapshot.fechaInicio() == null ? null : snapshot.fechaInicio().truncatedTo(ChronoUnit.MICROS));
                job.setFechaFin(snapshot.fechaFin() == null ? null : snapshot.fechaFin().truncatedTo(ChronoUnit.MICROS));
                if (snapshot.porcentaje() != null) job.setPorcentaje(snapshot.porcentaje());
                if (snapshot.fotogramasProcesados() != null) job.setFotogramasProcesados(snapshot.fotogramasProcesados());
                if (snapshot.fotogramasTotales() != null) job.setFotogramasTotales(snapshot.fotogramasTotales());
                job.setResultadosDisponibles(snapshot.resultadosDisponibles());
                job.setResultadoRef(snapshot.resultadoRef());
                job.setFechaActualizacion(clock.instant());
                if (snapshot.estado() == EstadoAnalisis.ERROR) {
                    var reason = "VIDEO_INVALIDO".equals(snapshot.codigoError()) ? INVALID_VIDEO : PROCESSING;
                    job.setCodigoError(reason.code());
                    job.setMensajeError(reason.message());
                }
            }
            job.setEnviado(true);
            job.setFallosConsecutivos(0);
            job.setProximaEjecucion(job.getEstado().isTerminal() ? null : clock.instant().plus(properties.getPollInterval()));
        }
        release(job);
        jobs.saveAndFlush(job);
    }

    private void fail(Work work, AnalysisGatewayException.Reason reason) {
        var job = owned(work);
        if (job == null) return;
        int failures = job.getFallosConsecutivos() + 1;
        job.setFallosConsecutivos(failures);
        if (!clock.instant().isBefore(work.deadline())) finishError(job, DEADLINE);
        else if (!reason.retryable()) finishError(job, reason);
        else if (failures >= properties.getMaxFailures()) finishError(job, RETRIES);
        else {
            // Backoff is bounded, and the overall deadline is checked independently.
            long delay = Math.min(300_000L, properties.getPollInterval().toMillis()
                    * (1L << Math.min(failures - 1, 10)));
            job.setProximaEjecucion(clock.instant().plusMillis(delay));
        }
        release(job);
        jobs.saveAndFlush(job);
    }

    private AnalysisJob owned(Work work) {
        var job = jobs.findLockedById(work.id()).orElse(null);
        if (job == null || job.getEstado().isTerminal() || !work.token().equals(job.getClaimToken())
                || job.getLeaseUntil() == null || !job.getLeaseUntil().isAfter(clock.instant())) return null;
        return job;
    }

    private void finishError(AnalysisJob job, AnalysisGatewayException.Reason reason) {
        job.setEstado(EstadoAnalisis.ERROR);
        job.setFechaFin(clock.instant());
        job.setFechaActualizacion(clock.instant());
        job.setCodigoError(reason.code());
        job.setMensajeError(reason.message());
        job.setResultadosDisponibles(false);
        job.setResultadoRef(null);
        job.setProximaEjecucion(null);
    }

    private void release(AnalysisJob job) {
        job.setClaimToken(null);
        job.setLeaseUntil(null);
    }
}

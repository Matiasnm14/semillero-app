package edu.upb.semillero.service;

import edu.upb.semillero.config.VideoProperties;
import edu.upb.semillero.config.AnalysisProperties;
import edu.upb.semillero.repository.dto.response.VideoPartidoResponseDto;
import edu.upb.semillero.repository.entity.AnalysisJob;
import edu.upb.semillero.repository.entity.VideoPartido;
import edu.upb.semillero.repository.enums.EstadoAnalisis;
import edu.upb.semillero.repository.enums.EstadoYoutube;
import edu.upb.semillero.repository.repos.AnalysisJobRepository;
import edu.upb.semillero.repository.repos.PartidoRepository;
import edu.upb.semillero.repository.repos.VideoPartidoRepository;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.AnalysisUploadException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import edu.upb.semillero.service.exception.StorageUnavailableException;
import edu.upb.semillero.service.exception.VideoTooLargeException;
import edu.upb.semillero.service.video.LocalVideoStorage;
import edu.upb.semillero.service.video.VideoInspector;
import edu.upb.semillero.service.analysis.AnalysisConcurrencyLimiter;
import edu.upb.semillero.service.analysis.AnalysisGateway;
import edu.upb.semillero.service.analysis.AnalysisGatewayException;
import edu.upb.semillero.service.analysis.AnalysisResultValidator;
import edu.upb.semillero.service.analysis.HttpAnalysisGateway;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class VideoPartidoService {
    private final VideoPartidoRepository videos;
    private final PartidoRepository partidos;
    private final LocalVideoStorage storage;
    private final VideoInspector inspector;
    private final VideoProperties properties;
    private final TransactionTemplate transaction;
    private final AnalysisJobRepository analyses;
    private final AnalysisGateway gateway;
    private final AnalysisResultValidator validator;
    private final AnalysisProperties analysisProperties;
    private final AnalysisConcurrencyLimiter capacity;
    private final Clock clock;

    public VideoPartidoService(VideoPartidoRepository videos, PartidoRepository partidos,
            LocalVideoStorage storage, VideoInspector inspector, VideoProperties properties,
            PlatformTransactionManager transactionManager, AnalysisJobRepository analyses, AnalysisGateway gateway,
            AnalysisResultValidator validator, AnalysisProperties analysisProperties, AnalysisConcurrencyLimiter capacity,
            @Qualifier("analysisClock") Clock clock) {
        this.videos = videos;
        this.partidos = partidos;
        this.storage = storage;
        this.inspector = inspector;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.analyses = analyses;
        this.gateway = gateway;
        this.validator = validator;
        this.analysisProperties = analysisProperties;
        this.capacity = capacity;
        this.clock = clock;
    }

    public VideoPartidoResponseDto create(Long partidoId, String filename, long declaredSize, InputStream input) {
        requirePartido(partidoId);
        if (!analysisProperties.isEnabled() || !analysisProperties.isEndpointValid()
                || !analysisProperties.isEvaluationPathValid()) {
            throw new StorageUnavailableException("El servicio de análisis no está habilitado o configurado");
        }
        String name = normalizeName(filename);
        if (declaredSize == 0) throw new BusinessException("El archivo de video está vacío");
        if (declaredSize > properties.getMaxBytes()) throw new VideoTooLargeException();
        var permit = capacity.tryAcquire();
        if (permit == null) throw new StorageUnavailableException("No hay capacidad de análisis disponible; intenta más tarde");
        try (permit) {
            Instant requested = now();
            var staged = storage.stage(input);
            try {
                var metadata = inspector.inspect(staged.path());
                Instant started = now();
                String payload = evaluate(staged);
                Instant finished = now();
                storage.commit(staged);
                // Copying, decoding and inference run without a SQL transaction. Both records become
                // visible together, so the YouTube worker cannot publish an unaccepted upload.
                return transaction.execute(status -> {
                    var partido = requirePartido(partidoId);
                    VideoPartido video = new VideoPartido();
                    video.setPartido(partido);
                    video.setStorageId(staged.storageId());
                    video.setNombreOriginal(name);
                    video.setFormato(metadata.format());
                    video.setTamanoBytes(staged.size());
                    video.setDuracionSegundos(metadata.durationSeconds());
                    video.setSha256(staged.sha256());
                    video.setFechaCarga(finished);
                    video.setTituloYoutube(partido.getFechaInicio().toLocalDate().toString());
                    video.setEstadoYoutube(properties.getYoutube().isEnabled()
                            ? EstadoYoutube.PENDIENTE : EstadoYoutube.DESHABILITADO);
                    if (properties.getYoutube().isEnabled()) video.setProximoIntento(finished);
                    video = videos.saveAndFlush(video);
                    var analysis = new AnalysisJob();
                    analysis.setVideo(video);
                    analysis.setEstado(EstadoAnalisis.COMPLETADO);
                    analysis.setFechaSolicitud(requested);
                    analysis.setFechaInicio(started);
                    analysis.setFechaFin(finished);
                    analysis.setFechaActualizacion(finished);
                    analysis.setEnviado(true);
                    analysis.setRespuestaJson(payload);
                    analysis.setResultadosDisponibles(false);
                    analyses.saveAndFlush(analysis);
                    return toResponse(video);
                });
            } catch (RuntimeException e) {
                // Includes failures raised by the transaction manager at commit, after saveAndFlush.
                storage.discard(staged.storageId());
                throw e;
            }
        }
    }

    private String evaluate(LocalVideoStorage.Staged staged) {
        CompletableFuture<String> future = null;
        try {
            future = gateway.evaluate(new AnalysisGateway.Submission(staged.storageId(), staged.path()));
            if (future == null) throw new AnalysisGatewayException(AnalysisGatewayException.Reason.INVALID_RESPONSE);
            return validator.validate(future.get(analysisProperties.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AnalysisUploadException(AnalysisGatewayException.Reason.INTERRUPTED);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new AnalysisUploadException(HttpAnalysisGateway.reason(e));
        } finally {
            // Cancel the exchange before compensating the file; cancellation cannot stop Python's inference.
            if (future != null && !future.isDone()) future.cancel(true);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    @Transactional(readOnly = true)
    public Page<VideoPartidoResponseDto> findAll(Long partidoId, Pageable pageable) {
        requirePartido(partidoId);
        return videos.findByPartidoId(partidoId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public VideoPartidoResponseDto findById(Long partidoId, Long id) {
        requirePartido(partidoId);
        return toResponse(require(partidoId, id));
    }

    @Transactional
    public VideoPartidoResponseDto retryYoutube(Long partidoId, Long id) {
        requirePartido(partidoId);
        var video = videos.findLockedById(id).filter(v -> v.getPartido().getId().equals(partidoId))
                .orElseThrow(() -> new NotFoundException("Video no encontrado para el partido indicado"));
        if (!properties.getYoutube().isEnabled()) {
            throw new ConflictException("La publicación en YouTube está deshabilitada");
        }
        if (video.getEstadoYoutube() == EstadoYoutube.REVISION_REQUERIDA) {
            throw new ConflictException("Revisa el recibo y el canal antes de reintentar para evitar duplicados");
        }
        if (video.getEstadoYoutube() != EstadoYoutube.ERROR
                && video.getEstadoYoutube() != EstadoYoutube.DESHABILITADO) {
            throw new ConflictException("El video ya está publicado o tiene una publicación en curso");
        }
        if (!storage.exists(video.getStorageId())) {
            throw new StorageUnavailableException("El archivo original no está disponible para reintentar");
        }
        video.setEstadoYoutube(EstadoYoutube.PENDIENTE);
        video.setErrorYoutube(null);
        video.setClaimToken(null);
        video.setProximoIntento(Instant.now());
        return toResponse(videos.saveAndFlush(video));
    }

    private edu.upb.semillero.repository.entity.Partido requirePartido(Long id) {
        return partidos.findById(id).orElseThrow(() -> new NotFoundException("Partido con id " + id + " no existe"));
    }

    private VideoPartido require(Long partidoId, Long id) {
        return videos.findByIdAndPartidoId(id, partidoId)
                .orElseThrow(() -> new NotFoundException("Video no encontrado para el partido indicado"));
    }

    private String normalizeName(String filename) {
        if (filename == null) throw new BusinessException("El archivo debe tener un nombre con extensión .mp4");
        String name = filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (name.length() > 255 || name.chars().anyMatch(Character::isISOControl)
                || !name.toLowerCase(Locale.ROOT).endsWith(".mp4") || name.equalsIgnoreCase(".mp4")) {
            throw new BusinessException("Por ahora se admiten archivos MP4 con un nombre válido de hasta 255 caracteres");
        }
        return name;
    }

    private VideoPartidoResponseDto toResponse(VideoPartido video) {
        String youtubeUrl = video.getYoutubeId() == null ? null : "https://www.youtube.com/watch?v=" + video.getYoutubeId();
        return new VideoPartidoResponseDto(video.getId(), video.getPartido().getId(), video.getNombreOriginal(),
                video.getFormato(), video.getTamanoBytes(), video.getDuracionSegundos(), video.getSha256(),
                video.getFechaCarga(), storage.exists(video.getStorageId()), video.getTituloYoutube(),
                video.getEstadoYoutube(), video.getYoutubeId(), youtubeUrl, video.getErrorYoutube());
    }
}

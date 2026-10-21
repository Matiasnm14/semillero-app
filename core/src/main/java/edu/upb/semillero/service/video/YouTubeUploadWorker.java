package edu.upb.semillero.service.video;

import edu.upb.semillero.config.VideoProperties;
import edu.upb.semillero.repository.enums.EstadoYoutube;
import edu.upb.semillero.repository.repos.VideoPartidoRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class YouTubeUploadWorker {
    private static final Logger log = LoggerFactory.getLogger(YouTubeUploadWorker.class);
    private static final List<EstadoYoutube> QUEUED = List.of(
            EstadoYoutube.PENDIENTE, EstadoYoutube.SUBIENDO, EstadoYoutube.PROCESANDO);
    private final VideoPartidoRepository videos;
    private final LocalVideoStorage storage;
    private final YouTubePublisher publisher;
    private final VideoProperties properties;
    private final TransactionTemplate transaction;

    private record Work(Long id, String token, YouTubePublisher.Publication publication) {}

    public YouTubeUploadWorker(VideoPartidoRepository videos, LocalVideoStorage storage,
            YouTubePublisher publisher, VideoProperties properties, PlatformTransactionManager transactionManager) {
        this.videos = videos;
        this.storage = storage;
        this.publisher = publisher;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${video.youtube.poll-ms:15000}", initialDelayString = "${video.youtube.poll-ms:15000}")
    public void poll() {
        if (properties.getYoutube().isWorkerEnabled()) processNext();
    }

    public boolean processNext() {
        if (!properties.getYoutube().isEnabled()) return false;
        for (var candidate : videos.findTop10ByEstadoYoutubeInAndProximoIntentoLessThanEqualOrderById(QUEUED, Instant.now())) {
            Work work = transaction.execute(status -> {
                var video = videos.findLockedById(candidate.getId()).orElse(null);
                Instant now = Instant.now();
                if (video == null || !QUEUED.contains(video.getEstadoYoutube())
                        || video.getProximoIntento() == null || video.getProximoIntento().isAfter(now)) return null;
                String token = UUID.randomUUID().toString();
                video.setClaimToken(token);
                video.setEstadoYoutube(EstadoYoutube.SUBIENDO);
                // Expired claims survive restart and are recoverable by another poller.
                video.setProximoIntento(now.plus(properties.getYoutube().getTimeout()).plusSeconds(60));
                videos.saveAndFlush(video);
                return new Work(video.getId(), token, new YouTubePublisher.Publication(
                        storage.original(video.getStorageId()), storage.receipt(video.getStorageId()), video.getTituloYoutube()));
            });
            if (work == null) continue;
            YouTubePublisher.Result result = null;
            String error = null;
            EstadoYoutube state;
            try {
                result = publisher.publish(work.publication());
                state = result.state();
            } catch (YouTubePublicationException e) {
                state = e.isReviewRequired() ? EstadoYoutube.REVISION_REQUERIDA : EstadoYoutube.ERROR;
                error = e.getMessage();
            } catch (RuntimeException e) {
                state = EstadoYoutube.ERROR;
                error = "No se pudo completar la publicación; el original está guardado y puedes reintentar";
                // OAuth/process exceptions may contain secrets. Never log the exception or process output.
                log.warn("Publicación de YouTube interrumpida para video {}", work.id());
            }
            var finalResult = result;
            var finalState = state;
            var finalError = error;
            transaction.executeWithoutResult(status -> {
                var video = videos.findLockedById(work.id()).orElse(null);
                if (video == null || !work.token().equals(video.getClaimToken())) return;
                video.setEstadoYoutube(finalState);
                video.setClaimToken(null);
                video.setErrorYoutube(finalError);
                if (finalResult != null) video.setYoutubeId(finalResult.videoId());
                video.setProximoIntento(finalState == EstadoYoutube.PROCESANDO ? Instant.now().plusSeconds(30) : null);
                videos.saveAndFlush(video);
            });
            return true;
        }
        return false;
    }
}

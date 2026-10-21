package edu.upb.semillero.service;

import edu.upb.semillero.config.VideoProperties;
import edu.upb.semillero.repository.dto.response.VideoPartidoResponseDto;
import edu.upb.semillero.repository.entity.VideoPartido;
import edu.upb.semillero.repository.enums.EstadoYoutube;
import edu.upb.semillero.repository.repos.PartidoRepository;
import edu.upb.semillero.repository.repos.VideoPartidoRepository;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import edu.upb.semillero.service.exception.StorageUnavailableException;
import edu.upb.semillero.service.exception.VideoTooLargeException;
import edu.upb.semillero.service.video.LocalVideoStorage;
import edu.upb.semillero.service.video.VideoInspector;
import java.io.InputStream;
import java.time.Instant;
import java.util.Locale;
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

    public VideoPartidoService(VideoPartidoRepository videos, PartidoRepository partidos,
            LocalVideoStorage storage, VideoInspector inspector, VideoProperties properties,
            PlatformTransactionManager transactionManager) {
        this.videos = videos;
        this.partidos = partidos;
        this.storage = storage;
        this.inspector = inspector;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public VideoPartidoResponseDto create(Long partidoId, String filename, long declaredSize, InputStream input) {
        requirePartido(partidoId);
        String name = normalizeName(filename);
        if (declaredSize == 0) throw new BusinessException("El archivo de video está vacío");
        if (declaredSize > properties.getMaxBytes()) throw new VideoTooLargeException();
        var staged = storage.stage(input);
        try {
            var metadata = inspector.inspect(staged.path());
            storage.commit(staged);
            // No DB transaction or connection is held while copying or decoding the file.
            // Returning only after commit lets the catch also compensate constraint/commit failures.
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
                video.setFechaCarga(Instant.now());
                video.setTituloYoutube(partido.getFechaInicio().toLocalDate().toString());
                video.setEstadoYoutube(properties.getYoutube().isEnabled()
                        ? EstadoYoutube.PENDIENTE : EstadoYoutube.DESHABILITADO);
                if (properties.getYoutube().isEnabled()) video.setProximoIntento(Instant.now());
                return toResponse(videos.saveAndFlush(video));
            });
        } catch (RuntimeException e) {
            storage.discard(staged.storageId());
            throw e;
        }
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

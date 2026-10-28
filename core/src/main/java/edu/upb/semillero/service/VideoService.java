package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.VideoRequestDto;
import edu.upb.semillero.repository.dto.request.HighlightRequestDto;
import edu.upb.semillero.repository.dto.request.HighlightUrlRequestDto;
import edu.upb.semillero.repository.dto.response.HighlightResponseDto;
import edu.upb.semillero.repository.dto.response.VideoResponseDto;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.Video;
import edu.upb.semillero.repository.repos.VideoRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class VideoService {
    private static final Pattern YOUTUBE_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private final VideoRepository repository;
    private final EntityRelations relations;

    public VideoService(VideoRepository repository, EntityRelations relations) {
        this.repository = repository;
        this.relations = relations;
    }

    public Page<VideoResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public VideoResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    public Page<HighlightResponseDto> findHighlights(Long jugadorId, boolean incluirOcultos, Pageable pageable) {
        Page<Video> page = incluirOcultos
                ? repository.findByJugadorId(jugadorId, pageable)
                : repository.findByJugadorIdAndVisibleTrue(jugadorId, pageable);
        return page.map(this::toHighlightResponse);
    }

    @Transactional
    public HighlightResponseDto createHighlight(HighlightRequestDto request) {
        Video entity = new Video();
        entity.setJugador(relations.find(Jugador.class, request.jugadorId()));
        entity.setLinkVideo(request.youtubeUrl().trim());
        entity.setYoutubeVideoId(extractYoutubeVideoId(request.youtubeUrl()));
        entity.setVisible(request.visible() == null || request.visible());
        return toHighlightResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public HighlightResponseDto updateHighlightVisibility(Long id, boolean visible) {
        Video entity = require(id);
        entity.setVisible(visible);
        return toHighlightResponse(repository.saveAndFlush(entity));
    }

    public Long highlightPlayerId(Long id) { return relations.id(require(id).getJugador()); }

    @Transactional
    public HighlightResponseDto updateHighlightUrl(Long id, HighlightUrlRequestDto request) {
        Video entity = require(id);
        // The player relationship is deliberately not editable here. A video
        // remains associated with the player selected when it was created.
        entity.setLinkVideo(request.youtubeUrl().trim());
        entity.setYoutubeVideoId(extractYoutubeVideoId(request.youtubeUrl()));
        return toHighlightResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void deleteHighlight(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    @Transactional
    public VideoResponseDto create(VideoRequestDto request) {
        Video entity = new Video();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public VideoResponseDto update(Long id, VideoRequestDto request) {
        Video entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Video require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Video con id " + id + " no existe"));
    }

    private void apply(Video entity, VideoRequestDto request) {
        entity.setTituloVideo(request.tituloVideo());
        entity.setLinkVideo(request.linkVideo());
        entity.setJugador(relations.find(Jugador.class, request.jugadorId()));
    }

    private VideoResponseDto toResponse(Video entity) {
        return new VideoResponseDto(
                entity.getId(),
                entity.getTituloVideo(),
                entity.getLinkVideo(),
                relations.id(entity.getJugador()));
    }

    private HighlightResponseDto toHighlightResponse(Video entity) {
        return new HighlightResponseDto(
                entity.getId(),
                relations.id(entity.getJugador()),
                entity.getLinkVideo(),
                entity.getYoutubeVideoId(),
                entity.isVisible(),
                entity.getCreatedAt());
    }

    private String extractYoutubeVideoId(String url) {
        String input = url == null ? "" : url.trim();
        if (YOUTUBE_ID.matcher(input).matches()) return input;
        try {
            URI uri = new URI(input);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase().replaceFirst("^www\\.", "");
            String path = uri.getPath() == null ? "" : uri.getPath();
            String id = null;
            if (host.equals("youtu.be")) {
                String[] segments = path.split("/");
                if (segments.length > 1) id = segments[1];
            } else if (host.equals("youtube.com") || host.equals("m.youtube.com")) {
                if (path.equals("/watch")) {
                    String query = uri.getQuery();
                    if (query != null) for (String parameter : query.split("&")) {
                        if (parameter.startsWith("v=")) id = parameter.substring(2);
                    }
                } else {
                    String[] segments = path.split("/");
                    if (segments.length > 2 && (segments[1].equals("shorts")
                            || segments[1].equals("embed") || segments[1].equals("live"))) {
                        id = segments[2];
                    }
                }
            }
            if (id != null && YOUTUBE_ID.matcher(id).matches()) return id;
        } catch (URISyntaxException ignored) {
            // Se comunica el error de negocio uniforme debajo.
        }
        throw new edu.upb.semillero.service.exception.BusinessException(
                "El enlace debe ser un video válido de YouTube o YouTube Shorts");
    }
}

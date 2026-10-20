package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.VideoRequestDto;
import edu.upb.semillero.repository.dto.response.VideoResponseDto;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.Video;
import edu.upb.semillero.repository.repos.VideoRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VideoService {
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
}

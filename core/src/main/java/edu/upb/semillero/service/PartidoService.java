package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.PartidoRequestDto;
import edu.upb.semillero.repository.dto.response.PartidoResponseDto;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.repos.ParticipacionPartidoRepository;
import edu.upb.semillero.repository.repos.PartidoRepository;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PartidoService {
    private final PartidoRepository repository;
    private final EntityRelations relations;
    private final ParticipacionPartidoRepository participaciones;

    public PartidoService(PartidoRepository repository, EntityRelations relations, ParticipacionPartidoRepository participaciones) {
        this.repository = repository;
        this.relations = relations;
        this.participaciones = participaciones;
    }

    public Page<PartidoResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public PartidoResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public PartidoResponseDto create(PartidoRequestDto request) {
        Partido entity = new Partido();
        apply(entity, request);
        validate(entity);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public PartidoResponseDto update(Long id, PartidoRequestDto request) {
        Partido entity = require(id);
        apply(entity, request);
        validate(entity);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Partido require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Partido con id " + id + " no existe"));
    }

    private void apply(Partido entity, PartidoRequestDto request) {
        entity.setFechaInicio(request.fechaInicio());
        entity.setFechaFin(request.fechaFin());
        entity.setClubLocal(relations.find(Club.class, request.clubLocalId()));
        entity.setClubVisitante(relations.find(Club.class, request.clubVisitanteId()));
        entity.setEstado(request.estado());
    }

    private PartidoResponseDto toResponse(Partido entity) {
        return new PartidoResponseDto(
                entity.getId(),
                entity.getFechaInicio(),
                entity.getFechaFin(),
                relations.id(entity.getClubLocal()),
                relations.id(entity.getClubVisitante()),
                entity.getEstado());
    }

    private void validate(Partido entity) {
        if (entity.getFechaInicio() != null && entity.getFechaFin() != null
                && entity.getFechaFin().isBefore(entity.getFechaInicio())) {
            throw new BusinessException("La fecha final no puede ser anterior a la fecha inicial");
        }
        Long localId = entity.getClubLocal().getId();
        Long visitanteId = entity.getClubVisitante().getId();
        if (localId.equals(visitanteId)) {
            throw new BusinessException("El club local y visitante deben ser distintos");
        }
        if (entity.getId() != null && participaciones.existsByPartidoIdAndClubIdNotIn(
                entity.getId(), List.of(localId, visitanteId))) {
            throw new BusinessException("El cambio de clubes deja participaciones fuera del partido");
        }
    }
}

package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.FichadoRequestDto;
import edu.upb.semillero.repository.dto.response.FichadoResponseDto;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Fichado;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.repos.FichadoRepository;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FichadoService {
    private final FichadoRepository repository;
    private final EntityRelations relations;

    public FichadoService(FichadoRepository repository, EntityRelations relations) {
        this.repository = repository;
        this.relations = relations;
    }

    public Page<FichadoResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public FichadoResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public FichadoResponseDto create(FichadoRequestDto request) {
        Fichado entity = new Fichado();
        apply(entity, request);
        validate(entity);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public FichadoResponseDto update(Long id, FichadoRequestDto request) {
        Fichado entity = require(id);
        apply(entity, request);
        validate(entity);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Fichado require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Fichado con id " + id + " no existe"));
    }

    private void apply(Fichado entity, FichadoRequestDto request) {
        entity.setFechaFichaje(request.fechaFichaje());
        entity.setFechaFinalFichaje(request.fechaFinalFichaje());
        entity.setJugador(relations.find(Jugador.class, request.jugadorId()));
        entity.setClub(relations.find(Club.class, request.clubId()));
    }

    private FichadoResponseDto toResponse(Fichado entity) {
        return new FichadoResponseDto(
                entity.getId(),
                entity.getFechaFichaje(),
                entity.getFechaFinalFichaje(),
                relations.id(entity.getJugador()),
                relations.id(entity.getClub()));
    }

    private void validate(Fichado entity) {
        if (entity.getFechaFichaje() != null && entity.getFechaFinalFichaje() != null
                && entity.getFechaFinalFichaje().isBefore(entity.getFechaFichaje())) {
            throw new BusinessException("La fecha final no puede ser anterior a la fecha inicial");
        }
        var nacimiento = entity.getJugador().getFechaNacimiento();
        if (nacimiento != null && entity.getFechaFichaje() != null
                && entity.getFechaFichaje().toLocalDate().isBefore(nacimiento)) {
            throw new BusinessException(
                    "La fecha inicial del fichaje no puede ser anterior al nacimiento del jugador");
        }
        if (nacimiento != null && entity.getFechaFinalFichaje() != null
                && entity.getFechaFinalFichaje().toLocalDate().isBefore(nacimiento)) {
            throw new BusinessException(
                    "La fecha final del fichaje no puede ser anterior al nacimiento del jugador");
        }
    }
}

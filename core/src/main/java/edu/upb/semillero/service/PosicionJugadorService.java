package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.PosicionJugadorRequestDto;
import edu.upb.semillero.repository.dto.response.PosicionJugadorResponseDto;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.Posicion;
import edu.upb.semillero.repository.entity.PosicionJugador;
import edu.upb.semillero.repository.repos.PosicionJugadorRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PosicionJugadorService {
    private final PosicionJugadorRepository repository;
    private final EntityRelations relations;

    public PosicionJugadorService(PosicionJugadorRepository repository, EntityRelations relations) {
        this.repository = repository;
        this.relations = relations;
    }

    public Page<PosicionJugadorResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public PosicionJugadorResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public PosicionJugadorResponseDto create(PosicionJugadorRequestDto request) {
        PosicionJugador entity = new PosicionJugador();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public PosicionJugadorResponseDto update(Long id, PosicionJugadorRequestDto request) {
        PosicionJugador entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private PosicionJugador require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("PosicionJugador con id " + id + " no existe"));
    }

    private void apply(PosicionJugador entity, PosicionJugadorRequestDto request) {
        entity.setPosicion(relations.find(Posicion.class, request.posicionId()));
        entity.setJugador(relations.find(Jugador.class, request.jugadorId()));
    }

    private PosicionJugadorResponseDto toResponse(PosicionJugador entity) {
        return new PosicionJugadorResponseDto(
                entity.getId(),
                relations.id(entity.getPosicion()),
                relations.id(entity.getJugador()));
    }
}

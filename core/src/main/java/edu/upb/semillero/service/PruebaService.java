package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.PruebaRequestDto;
import edu.upb.semillero.repository.dto.response.PruebaResponseDto;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Prueba;
import edu.upb.semillero.repository.entity.UsuarioClub;
import edu.upb.semillero.repository.repos.PruebaRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PruebaService {
    private final PruebaRepository repository;
    private final EntityRelations relations;

    public PruebaService(PruebaRepository repository, EntityRelations relations) {
        this.repository = repository;
        this.relations = relations;
    }

    public Page<PruebaResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public PruebaResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public PruebaResponseDto create(PruebaRequestDto request) {
        Prueba entity = new Prueba();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public PruebaResponseDto update(Long id, PruebaRequestDto request) {
        Prueba entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Prueba require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Prueba con id " + id + " no existe"));
    }

    private void apply(Prueba entity, PruebaRequestDto request) {
        entity.setFechaPrueba(request.fechaPrueba());
        entity.setHoraPrueba(request.horaPrueba());
        entity.setClub(relations.find(Club.class, request.clubId()));
        entity.setUsuarioClub(relations.find(UsuarioClub.class, request.usuarioClubId()));
    }

    private PruebaResponseDto toResponse(Prueba entity) {
        return new PruebaResponseDto(
                entity.getId(),
                entity.getFechaPrueba(),
                entity.getHoraPrueba(),
                relations.id(entity.getClub()),
                relations.id(entity.getUsuarioClub()));
    }
}

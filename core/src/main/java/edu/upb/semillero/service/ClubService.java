package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.ClubRequestDto;
import edu.upb.semillero.repository.dto.response.ClubResponseDto;
import edu.upb.semillero.repository.entity.Ciudad;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.repos.ClubRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClubService {
    private final ClubRepository repository;
    private final EntityRelations relations;

    public ClubService(ClubRepository repository, EntityRelations relations) {
        this.repository = repository;
        this.relations = relations;
    }

    public Page<ClubResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public ClubResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public ClubResponseDto create(ClubRequestDto request) {
        Club entity = new Club();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public ClubResponseDto update(Long id, ClubRequestDto request) {
        Club entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Club require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Club con id " + id + " no existe"));
    }

    private void apply(Club entity, ClubRequestDto request) {
        entity.setNombreClub(request.nombreClub());
        entity.setCiudad(relations.find(Ciudad.class, request.ciudadId()));
    }

    private ClubResponseDto toResponse(Club entity) {
        return new ClubResponseDto(
                entity.getId(),
                entity.getNombreClub(),
                relations.id(entity.getCiudad()));
    }
}

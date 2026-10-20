package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.CiudadRequestDto;
import edu.upb.semillero.repository.dto.response.CiudadResponseDto;
import edu.upb.semillero.repository.entity.Ciudad;
import edu.upb.semillero.repository.entity.Pais;
import edu.upb.semillero.repository.repos.CiudadRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CiudadService {
    private final CiudadRepository repository;
    private final EntityRelations relations;

    public CiudadService(CiudadRepository repository, EntityRelations relations) {
        this.repository = repository;
        this.relations = relations;
    }

    public Page<CiudadResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public CiudadResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public CiudadResponseDto create(CiudadRequestDto request) {
        Ciudad entity = new Ciudad();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public CiudadResponseDto update(Long id, CiudadRequestDto request) {
        Ciudad entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Ciudad require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ciudad con id " + id + " no existe"));
    }

    private void apply(Ciudad entity, CiudadRequestDto request) {
        entity.setNombreCiudad(request.nombreCiudad());
        entity.setPais(relations.find(Pais.class, request.paisId()));
    }

    private CiudadResponseDto toResponse(Ciudad entity) {
        return new CiudadResponseDto(
                entity.getId(),
                entity.getNombreCiudad(),
                relations.id(entity.getPais()));
    }
}

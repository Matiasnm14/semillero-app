package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.SedeRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.SedeResponseDto;
import edu.upb.semillero.repository.entity.Ciudad;
import edu.upb.semillero.repository.entity.torneos.Sede;
import edu.upb.semillero.repository.repos.torneos.SedeRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 3 - Sedes donde se juegan los partidos (tarea S1-16).
 */
@Service
@Transactional(readOnly = true)
public class SedeService {
    private final SedeRepository repository;
    private final EntityRelations relations;

    public SedeService(SedeRepository repository, EntityRelations relations) {
        this.repository = repository;
        this.relations = relations;
    }

    public Page<SedeResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public SedeResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public SedeResponseDto create(SedeRequestDto request) {
        Sede entity = new Sede();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public SedeResponseDto update(Long id, SedeRequestDto request) {
        Sede entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Sede require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sede con id " + id + " no existe"));
    }

    private void apply(Sede entity, SedeRequestDto request) {
        entity.setNombre(request.nombre());
        entity.setDireccion(request.direccion());
        entity.setCiudad(relations.find(Ciudad.class, request.ciudadId()));
    }

    private SedeResponseDto toResponse(Sede entity) {
        return new SedeResponseDto(
                entity.getId(),
                entity.getNombre(),
                entity.getDireccion(),
                relations.id(entity.getCiudad()));
    }
}

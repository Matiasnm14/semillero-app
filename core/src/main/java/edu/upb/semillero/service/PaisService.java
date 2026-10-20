package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.PaisRequestDto;
import edu.upb.semillero.repository.dto.response.PaisResponseDto;
import edu.upb.semillero.repository.entity.Pais;
import edu.upb.semillero.repository.repos.PaisRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaisService {
    private final PaisRepository repository;

    public PaisService(PaisRepository repository) {
        this.repository = repository;
    }

    public Page<PaisResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public PaisResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public PaisResponseDto create(PaisRequestDto request) {
        Pais entity = new Pais();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public PaisResponseDto update(Long id, PaisRequestDto request) {
        Pais entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Pais require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pais con id " + id + " no existe"));
    }

    private void apply(Pais entity, PaisRequestDto request) {
        entity.setNombrePais(request.nombrePais());
    }

    private PaisResponseDto toResponse(Pais entity) {
        return new PaisResponseDto(
                entity.getId(),
                entity.getNombrePais());
    }
}

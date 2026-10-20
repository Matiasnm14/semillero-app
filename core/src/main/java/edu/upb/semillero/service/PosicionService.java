package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.PosicionRequestDto;
import edu.upb.semillero.repository.dto.response.PosicionResponseDto;
import edu.upb.semillero.repository.entity.Posicion;
import edu.upb.semillero.repository.repos.PosicionRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PosicionService {
    private final PosicionRepository repository;

    public PosicionService(PosicionRepository repository) {
        this.repository = repository;
    }

    public Page<PosicionResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public PosicionResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public PosicionResponseDto create(PosicionRequestDto request) {
        Posicion entity = new Posicion();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public PosicionResponseDto update(Long id, PosicionRequestDto request) {
        Posicion entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Posicion require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Posicion con id " + id + " no existe"));
    }

    private void apply(Posicion entity, PosicionRequestDto request) {
        entity.setPosicion(request.posicion());
        entity.setCodigoPosicion(request.codigoPosicion());
    }

    private PosicionResponseDto toResponse(Posicion entity) {
        return new PosicionResponseDto(
                entity.getId(),
                entity.getPosicion(),
                entity.getCodigoPosicion());
    }
}

package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.UsuarioTrabajaRequestDto;
import edu.upb.semillero.repository.dto.response.UsuarioTrabajaResponseDto;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.UsuarioClub;
import edu.upb.semillero.repository.entity.UsuarioTrabaja;
import edu.upb.semillero.repository.repos.UsuarioTrabajaRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UsuarioTrabajaService {
    private final UsuarioTrabajaRepository repository;
    private final EntityRelations relations;

    public UsuarioTrabajaService(UsuarioTrabajaRepository repository, EntityRelations relations) {
        this.repository = repository;
        this.relations = relations;
    }

    public Page<UsuarioTrabajaResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public UsuarioTrabajaResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public UsuarioTrabajaResponseDto create(UsuarioTrabajaRequestDto request) {
        UsuarioTrabaja entity = new UsuarioTrabaja();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public UsuarioTrabajaResponseDto update(Long id, UsuarioTrabajaRequestDto request) {
        UsuarioTrabaja entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private UsuarioTrabaja require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("UsuarioTrabaja con id " + id + " no existe"));
    }

    private void apply(UsuarioTrabaja entity, UsuarioTrabajaRequestDto request) {
        entity.setUsuarioClub(relations.find(UsuarioClub.class, request.usuarioClubId()));
        entity.setTipoUsuarioClub(request.tipoUsuarioClub());
        entity.setClub(relations.find(Club.class, request.clubId()));
    }

    private UsuarioTrabajaResponseDto toResponse(UsuarioTrabaja entity) {
        return new UsuarioTrabajaResponseDto(
                entity.getId(),
                relations.id(entity.getUsuarioClub()),
                entity.getTipoUsuarioClub(),
                relations.id(entity.getClub()));
    }
}

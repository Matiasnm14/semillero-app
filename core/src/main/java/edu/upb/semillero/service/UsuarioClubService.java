package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.UsuarioClubRequestDto;
import edu.upb.semillero.repository.dto.response.UsuarioClubResponseDto;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.UsuarioClub;
import edu.upb.semillero.repository.repos.UsuarioClubRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UsuarioClubService {
    private final UsuarioClubRepository repository;
    private final EntityRelations relations;

    public UsuarioClubService(UsuarioClubRepository repository, EntityRelations relations) {
        this.repository = repository;
        this.relations = relations;
    }

    public Page<UsuarioClubResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public UsuarioClubResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public UsuarioClubResponseDto create(UsuarioClubRequestDto request) {
        UsuarioClub entity = new UsuarioClub();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public UsuarioClubResponseDto update(Long id, UsuarioClubRequestDto request) {
        UsuarioClub entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private UsuarioClub require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("UsuarioClub con id " + id + " no existe"));
    }

    private void apply(UsuarioClub entity, UsuarioClubRequestDto request) {
        entity.setUsuario(relations.find(Usuario.class, request.usuarioId()));
    }

    private UsuarioClubResponseDto toResponse(UsuarioClub entity) {
        return new UsuarioClubResponseDto(
                entity.getId(),
                relations.id(entity.getUsuario()));
    }
}

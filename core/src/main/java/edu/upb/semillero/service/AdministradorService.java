package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.AdministradorRequestDto;
import edu.upb.semillero.repository.dto.response.AdministradorResponseDto;
import edu.upb.semillero.repository.entity.Administrador;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.repos.AdministradorRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdministradorService {
    private final AdministradorRepository repository;
    private final EntityRelations relations;

    public AdministradorService(AdministradorRepository repository, EntityRelations relations) {
        this.repository = repository;
        this.relations = relations;
    }

    public Page<AdministradorResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public AdministradorResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public AdministradorResponseDto create(AdministradorRequestDto request) {
        Administrador entity = new Administrador();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public AdministradorResponseDto update(Long id, AdministradorRequestDto request) {
        Administrador entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Administrador require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Administrador con id " + id + " no existe"));
    }

    private void apply(Administrador entity, AdministradorRequestDto request) {
        entity.setUsuario(relations.find(Usuario.class, request.usuarioId()));
    }

    private AdministradorResponseDto toResponse(Administrador entity) {
        return new AdministradorResponseDto(
                entity.getId(),
                relations.id(entity.getUsuario()));
    }
}

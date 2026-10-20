package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.ConversacionRequestDto;
import edu.upb.semillero.repository.dto.response.ConversacionResponseDto;
import edu.upb.semillero.repository.entity.Conversacion;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.repos.ConversacionRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConversacionService {
    private final ConversacionRepository repository;
    private final EntityRelations relations;

    public ConversacionService(ConversacionRepository repository, EntityRelations relations) {
        this.repository = repository;
        this.relations = relations;
    }

    public Page<ConversacionResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public ConversacionResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public ConversacionResponseDto create(ConversacionRequestDto request) {
        Conversacion entity = new Conversacion();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public ConversacionResponseDto update(Long id, ConversacionRequestDto request) {
        Conversacion entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Conversacion require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Conversacion con id " + id + " no existe"));
    }

    private void apply(Conversacion entity, ConversacionRequestDto request) {
        entity.setMensaje(request.mensaje());
        entity.setUsuarioEnvia(relations.find(Usuario.class, request.usuarioEnviaId()));
        entity.setUsuarioRecibe(relations.find(Usuario.class, request.usuarioRecibeId()));
    }

    private ConversacionResponseDto toResponse(Conversacion entity) {
        return new ConversacionResponseDto(
                entity.getId(),
                entity.getMensaje(),
                relations.id(entity.getUsuarioEnvia()),
                relations.id(entity.getUsuarioRecibe()));
    }
}

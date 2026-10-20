package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.JugadorRequestDto;
import edu.upb.semillero.repository.dto.response.JugadorResponseDto;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JugadorService {
    private final JugadorRepository repository;
    private final EntityRelations relations;

    public JugadorService(JugadorRepository repository, EntityRelations relations) {
        this.repository = repository;
        this.relations = relations;
    }

    public Page<JugadorResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public JugadorResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public JugadorResponseDto create(JugadorRequestDto request) {
        Jugador entity = new Jugador();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public JugadorResponseDto update(Long id, JugadorRequestDto request) {
        Jugador entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Jugador require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Jugador con id " + id + " no existe"));
    }

    private void apply(Jugador entity, JugadorRequestDto request) {
        entity.setPiernaHabil(request.piernaHabil());
        entity.setFechaNacimiento(request.fechaNacimiento());
        entity.setAltura(request.altura());
        entity.setPeso(request.peso());
        entity.setDescripcionJugador(request.descripcionJugador());
        entity.setUsuario(relations.find(Usuario.class, request.usuarioId()));
    }

    private JugadorResponseDto toResponse(Jugador entity) {
        return new JugadorResponseDto(
                entity.getId(),
                entity.getPiernaHabil(),
                entity.getFechaNacimiento(),
                entity.getAltura(),
                entity.getPeso(),
                entity.getDescripcionJugador(),
                relations.id(entity.getUsuario()));
    }
}

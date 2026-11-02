package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.DocumentoJugadorRequestDto;
import edu.upb.semillero.repository.dto.response.DocumentoJugadorResponseDto;
import edu.upb.semillero.repository.entity.DocumentoJugador;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.enums.EstadoDocumentoJugador;
import edu.upb.semillero.repository.repos.DocumentoJugadorRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de los documentos del jugador (ficha técnica). Torneos no lo usa para
 * escribir: lee la tabla a través de {@code FichaTecnicaService}.
 */
@Service
@Transactional(readOnly = true)
public class DocumentoJugadorService {
    private final DocumentoJugadorRepository repository;
    private final JugadorRepository jugadores;

    public DocumentoJugadorService(DocumentoJugadorRepository repository, JugadorRepository jugadores) {
        this.repository = repository;
        this.jugadores = jugadores;
    }

    public Page<DocumentoJugadorResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public DocumentoJugadorResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public DocumentoJugadorResponseDto create(DocumentoJugadorRequestDto request) {
        DocumentoJugador entity = new DocumentoJugador();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public DocumentoJugadorResponseDto update(Long id, DocumentoJugadorRequestDto request) {
        DocumentoJugador entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private DocumentoJugador require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Documento con id " + id + " no existe"));
    }

    private void apply(DocumentoJugador entity, DocumentoJugadorRequestDto request) {
        Jugador jugador = jugadores.findById(request.jugadorId())
                .orElseThrow(() -> new NotFoundException(
                        "Jugador con id " + request.jugadorId() + " no existe"));
        entity.setJugador(jugador);
        entity.setTipo(request.tipo());
        entity.setNumero(request.numero());
        entity.setFechaEmision(request.fechaEmision());
        entity.setFechaVencimiento(request.fechaVencimiento());
        entity.setEstado(request.estado() == null ? EstadoDocumentoJugador.PENDIENTE : request.estado());
    }

    private DocumentoJugadorResponseDto toResponse(DocumentoJugador entity) {
        return new DocumentoJugadorResponseDto(
                entity.getId(),
                entity.getJugador().getId(),
                entity.getTipo(),
                entity.getNumero(),
                entity.getFechaEmision(),
                entity.getFechaVencimiento(),
                entity.getEstado());
    }
}

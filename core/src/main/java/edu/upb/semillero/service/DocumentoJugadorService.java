package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.DocumentoJugadorRequestDto;
import edu.upb.semillero.repository.dto.response.DocumentoJugadorResponseDto;
import edu.upb.semillero.repository.entity.DocumentoJugador;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.enums.EstadoDocumentoJugador;
import edu.upb.semillero.repository.repos.DocumentoJugadorRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import edu.upb.semillero.service.seguridad.AccesoDocumentos;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de los documentos del jugador (ficha técnica). Torneos no lo usa para
 * escribir: lee la tabla a través de {@code FichaTecnicaService}.
 *
 * <p>HU 16: la autenticación la exige {@code SeguridadConfiguration}; aquí se
 * controla, por jugador, quién puede ver y administrar cada documento, y que
 * aprobar a VIGENTE quede reservado al administrador (ver {@link AccesoDocumentos}).
 */
@Service
@Transactional(readOnly = true)
public class DocumentoJugadorService {
    private static final String RUTA = "/api/v1/documentos-jugador";

    private final DocumentoJugadorRepository repository;
    private final JugadorRepository jugadores;
    private final AccesoDocumentos acceso;

    public DocumentoJugadorService(DocumentoJugadorRepository repository, JugadorRepository jugadores,
            AccesoDocumentos acceso) {
        this.repository = repository;
        this.jugadores = jugadores;
        this.acceso = acceso;
    }

    public Page<DocumentoJugadorResponseDto> findAll(Pageable pageable) {
        Set<Long> permitidos = acceso.jugadoresAdministrables();
        if (permitidos == null) {          // administrador: ve todos
            return repository.findAll(pageable).map(this::toResponse);
        }
        if (permitidos.isEmpty()) {
            return Page.empty(pageable);
        }
        return repository.findByJugadorIdIn(permitidos, pageable).map(this::toResponse);
    }

    public DocumentoJugadorResponseDto findById(Long id) {
        DocumentoJugador entity = require(id);
        acceso.requireAdministrar("GET", RUTA + "/" + id, entity.getJugador().getId());
        return toResponse(entity);
    }

    @Transactional
    public DocumentoJugadorResponseDto create(DocumentoJugadorRequestDto request) {
        acceso.requireAdministrar("POST", RUTA, request.jugadorId());
        if (request.estado() == EstadoDocumentoJugador.VIGENTE) {
            acceso.requireAprobacion("POST", RUTA);
        }
        DocumentoJugador entity = new DocumentoJugador();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public DocumentoJugadorResponseDto update(Long id, DocumentoJugadorRequestDto request) {
        DocumentoJugador entity = require(id);
        acceso.requireAdministrar("PUT", RUTA + "/" + id, entity.getJugador().getId());
        // Cambiar jugadorId no debe trasladar el documento a un jugador ajeno.
        if (!entity.getJugador().getId().equals(request.jugadorId())) {
            acceso.requireAdministrar("PUT", RUTA + "/" + id, request.jugadorId());
        }
        if (request.estado() == EstadoDocumentoJugador.VIGENTE) {
            acceso.requireAprobacion("PUT", RUTA + "/" + id);
        }
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        DocumentoJugador entity = require(id);
        acceso.requireAdministrar("DELETE", RUTA + "/" + id, entity.getJugador().getId());
        repository.delete(entity);
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

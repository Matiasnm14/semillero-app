package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.TorneoRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.TorneoResponseDto;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.enums.torneos.AmbitoTorneo;
import edu.upb.semillero.repository.enums.torneos.EstadoTorneo;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 1 - Crear torneo (tareas S1-08, S1-09 y S1-10). El torneo nace en
 * BORRADOR y no es visible para los equipos hasta abrir inscripciones.
 */
@Service
@Transactional(readOnly = true)
public class TorneoService {
    private final TorneoRepository repository;
    private final TorneoCategoriaRepository categorias;
    private final TorneoCategoriaService categoriaService;
    private final EntityRelations relations;

    public TorneoService(TorneoRepository repository, TorneoCategoriaRepository categorias,
            TorneoCategoriaService categoriaService, EntityRelations relations) {
        this.repository = repository;
        this.categorias = categorias;
        this.categoriaService = categoriaService;
        this.relations = relations;
    }

    public Page<TorneoResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public TorneoResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public TorneoResponseDto create(TorneoRequestDto request) {
        Torneo entity = new Torneo();
        apply(entity, request);
        entity.setEstado(EstadoTorneo.BORRADOR);
        entity.setFechaCreacion(LocalDateTime.now());
        validate(entity);
        Torneo creado = repository.saveAndFlush(entity);
        if (request.categorias() != null) {
            request.categorias().forEach(categoria -> categoriaService.create(creado.getId(), categoria));
        }
        return toResponse(creado);
    }

    @Transactional
    public TorneoResponseDto update(Long id, TorneoRequestDto request) {
        Torneo entity = require(id);
        apply(entity, request);
        validate(entity);
        return toResponse(repository.saveAndFlush(entity));
    }

    /** Tarea S1-10: el único cambio de estado de la HU 1. */
    @Transactional
    public TorneoResponseDto abrirInscripciones(Long id) {
        Torneo entity = require(id);
        if (entity.getEstado() != EstadoTorneo.BORRADOR) {
            throw new ConflictException("Solo se abren las inscripciones de un torneo en BORRADOR; el torneo "
                    + id + " está en " + entity.getEstado());
        }
        if (!categorias.existsByTorneoId(id)) {
            throw new BusinessException("El torneo necesita al menos una categoría para abrir inscripciones");
        }
        entity.setEstado(EstadoTorneo.INSCRIPCIONES_ABIERTAS);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Torneo require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Torneo con id " + id + " no existe"));
    }

    private void apply(Torneo entity, TorneoRequestDto request) {
        entity.setNombre(request.nombre());
        entity.setTemporada(request.temporada());
        entity.setTipo(request.tipo());
        entity.setAmbito(request.ambito());
        entity.setOrganizadorExterno(
                request.ambito() == AmbitoTorneo.EXTERNO ? request.organizadorExterno() : null);
        entity.setFormato(request.formato());
        entity.setIdaVuelta(request.idaVuelta() != null && request.idaVuelta());
        entity.setFechaInicio(request.fechaInicio());
        entity.setFechaFin(request.fechaFin());
        entity.setFechaLimiteInscripcion(request.fechaLimiteInscripcion());
        entity.setUsuario(relations.find(Usuario.class, request.usuarioId()));
    }

    private TorneoResponseDto toResponse(Torneo entity) {
        return new TorneoResponseDto(
                entity.getId(),
                entity.getNombre(),
                entity.getTemporada(),
                entity.getTipo(),
                entity.getAmbito(),
                entity.getOrganizadorExterno(),
                entity.getFormato(),
                entity.getIdaVuelta(),
                entity.getFechaInicio(),
                entity.getFechaFin(),
                entity.getFechaLimiteInscripcion(),
                entity.getEstado(),
                relations.id(entity.getUsuario()),
                entity.getFechaCreacion());
    }

    private void validate(Torneo entity) {
        if (entity.getFechaFin().isBefore(entity.getFechaInicio())) {
            throw new BusinessException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }
        if (entity.getFechaLimiteInscripcion().isAfter(entity.getFechaInicio())) {
            throw new BusinessException(
                    "La fecha límite de inscripción no puede ser posterior al inicio del torneo");
        }
        if (entity.getAmbito() == AmbitoTorneo.EXTERNO
                && (entity.getOrganizadorExterno() == null || entity.getOrganizadorExterno().isBlank())) {
            throw new BusinessException("Un torneo de ámbito EXTERNO exige el organizador externo");
        }
    }
}

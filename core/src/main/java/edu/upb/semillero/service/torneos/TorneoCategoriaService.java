package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.TorneoCategoriaRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.TorneoCategoriaResponseDto;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tarea S1-09: categorías de un torneo con su rango de edad. Cada torneo define
 * las suyas; no hay tabla común de categorías.
 */
@Service
@Transactional(readOnly = true)
public class TorneoCategoriaService {
    private final TorneoCategoriaRepository repository;
    private final TorneoRepository torneos;
    private final EntityRelations relations;

    public TorneoCategoriaService(TorneoCategoriaRepository repository, TorneoRepository torneos,
            EntityRelations relations) {
        this.repository = repository;
        this.torneos = torneos;
        this.relations = relations;
    }

    public Page<TorneoCategoriaResponseDto> findAllByTorneo(Long torneoId, Pageable pageable) {
        requireTorneo(torneoId);
        return repository.findByTorneoId(torneoId, pageable).map(this::toResponse);
    }

    public TorneoCategoriaResponseDto findById(Long torneoId, Long id) {
        return toResponse(require(torneoId, id));
    }

    @Transactional
    public TorneoCategoriaResponseDto create(Long torneoId, TorneoCategoriaRequestDto request) {
        TorneoCategoria entity = new TorneoCategoria();
        entity.setTorneo(requireTorneo(torneoId));
        apply(entity, request);
        validate(entity);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public TorneoCategoriaResponseDto update(Long torneoId, Long id, TorneoCategoriaRequestDto request) {
        TorneoCategoria entity = require(torneoId, id);
        apply(entity, request);
        validate(entity);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long torneoId, Long id) {
        repository.delete(require(torneoId, id));
        repository.flush();
    }

    private Torneo requireTorneo(Long torneoId) {
        return torneos.findById(torneoId)
                .orElseThrow(() -> new NotFoundException("Torneo con id " + torneoId + " no existe"));
    }

    private TorneoCategoria require(Long torneoId, Long id) {
        return repository.findByIdAndTorneoId(id, torneoId)
                .orElseThrow(() -> new NotFoundException(
                        "La categoría con id " + id + " no existe en el torneo " + torneoId));
    }

    private void apply(TorneoCategoria entity, TorneoCategoriaRequestDto request) {
        entity.setNombre(request.nombre());
        entity.setEdadMinima(request.edadMinima());
        entity.setEdadMaxima(request.edadMaxima());
        entity.setCupoMaximo(request.cupoMaximo());
        entity.setDuracionTiempoMin(request.duracionTiempoMin());
        entity.setJugadoresEnCancha(request.jugadoresEnCancha());
        entity.setMaxSustituciones(request.maxSustituciones());
    }

    private TorneoCategoriaResponseDto toResponse(TorneoCategoria entity) {
        return new TorneoCategoriaResponseDto(
                entity.getId(),
                relations.id(entity.getTorneo()),
                entity.getNombre(),
                entity.getEdadMinima(),
                entity.getEdadMaxima(),
                entity.getCupoMaximo(),
                entity.getDuracionTiempoMin(),
                entity.getJugadoresEnCancha(),
                entity.getMaxSustituciones());
    }

    private void validate(TorneoCategoria entity) {
        if (entity.getEdadMinima() != null && entity.getEdadMinima() > entity.getEdadMaxima()) {
            throw new BusinessException("La edad mínima no puede ser mayor que la edad máxima");
        }
        if (entity.getCupoMaximo() < 2) {
            throw new BusinessException("El cupo máximo de la categoría debe ser al menos 2");
        }
        repository.findByTorneoIdAndNombre(entity.getTorneo().getId(), entity.getNombre())
                .filter(existing -> !Objects.equals(existing.getId(), entity.getId()))
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "La categoría " + entity.getNombre() + " ya existe en el torneo");
                });
    }
}

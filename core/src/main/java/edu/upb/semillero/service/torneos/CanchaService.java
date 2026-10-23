package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.CanchaRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.CanchaResponseDto;
import edu.upb.semillero.repository.entity.torneos.Cancha;
import edu.upb.semillero.repository.entity.torneos.Sede;
import edu.upb.semillero.repository.repos.torneos.CanchaRepository;
import edu.upb.semillero.repository.repos.torneos.ReprogramacionRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 3 - Canchas con su superficie y descripción (tareas S1-16 y S1-17).
 *
 * <p>La tarea S1-18 ("no eliminar una cancha con partidos programados") queda a
 * medias: hoy solo se puede comprobar contra {@code reprogramaciones}, porque
 * {@code partidos.canchas_id} todavía no existe. Ver docs/torneos/PENDIENTES.md.
 */
@Service
@Transactional(readOnly = true)
public class CanchaService {
    private final CanchaRepository repository;
    private final ReprogramacionRepository reprogramaciones;
    private final EntityRelations relations;

    public CanchaService(CanchaRepository repository, ReprogramacionRepository reprogramaciones,
            EntityRelations relations) {
        this.repository = repository;
        this.reprogramaciones = reprogramaciones;
        this.relations = relations;
    }

    public Page<CanchaResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public Page<CanchaResponseDto> findAllBySede(Long sedeId, Pageable pageable) {
        relations.find(Sede.class, sedeId);
        return repository.findBySedeId(sedeId, pageable).map(this::toResponse);
    }

    public CanchaResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public CanchaResponseDto create(CanchaRequestDto request) {
        Cancha entity = new Cancha();
        apply(entity, request);
        validate(entity);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public CanchaResponseDto update(Long id, CanchaRequestDto request) {
        Cancha entity = require(id);
        apply(entity, request);
        validate(entity);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        Cancha entity = require(id);
        if (reprogramaciones.existsByCanchaAnteriorIdOrCanchaNuevaId(id, id)) {
            throw new ConflictException("La cancha " + entity.getNombre()
                    + " tiene reprogramaciones registradas y no se puede eliminar");
        }
        repository.delete(entity);
        repository.flush();
    }

    private Cancha require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cancha con id " + id + " no existe"));
    }

    private void apply(Cancha entity, CanchaRequestDto request) {
        entity.setSede(relations.find(Sede.class, request.sedeId()));
        entity.setNombre(request.nombre());
        entity.setTipoSuperficie(request.tipoSuperficie());
        entity.setLargoM(request.largoM());
        entity.setAnchoM(request.anchoM());
        entity.setTechada(request.techada() != null && request.techada());
        entity.setIluminacion(request.iluminacion() != null && request.iluminacion());
        entity.setVestuarios(request.vestuarios() != null && request.vestuarios());
        entity.setDescripcion(request.descripcion());
        entity.setActiva(request.activa() == null || request.activa());
    }

    private CanchaResponseDto toResponse(Cancha entity) {
        return new CanchaResponseDto(
                entity.getId(),
                relations.id(entity.getSede()),
                entity.getNombre(),
                entity.getTipoSuperficie(),
                entity.getLargoM(),
                entity.getAnchoM(),
                entity.getTechada(),
                entity.getIluminacion(),
                entity.getVestuarios(),
                entity.getDescripcion(),
                entity.getActiva());
    }

    private void validate(Cancha entity) {
        repository.findBySedeIdAndNombre(entity.getSede().getId(), entity.getNombre())
                .filter(existing -> !Objects.equals(existing.getId(), entity.getId()))
                .ifPresent(existing -> {
                    throw new ConflictException("La sede ya tiene una cancha llamada " + entity.getNombre());
                });
    }
}

package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.InscripcionEquipoRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.InscripcionEquipoResponseDto;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.torneos.Grupo;
import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.torneos.EstadoInscripcion;
import edu.upb.semillero.repository.enums.torneos.EstadoTorneo;
import edu.upb.semillero.repository.repos.torneos.InscripcionEquipoRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 4 - Inscribir un equipo (tareas S1-20 y S1-21).
 *
 * <p>El cambio de estado con motivo (aprobar, observar o rechazar) es de la
 * HU 6, tarea S2-11, y no está acá: la inscripción nace PENDIENTE.
 */
@Service
@Transactional(readOnly = true)
public class InscripcionEquipoService {
    private final InscripcionEquipoRepository repository;
    private final TorneoCategoriaRepository categorias;
    private final EntityRelations relations;

    public InscripcionEquipoService(InscripcionEquipoRepository repository,
            TorneoCategoriaRepository categorias, EntityRelations relations) {
        this.repository = repository;
        this.categorias = categorias;
        this.relations = relations;
    }

    public Page<InscripcionEquipoResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public Page<InscripcionEquipoResponseDto> findAllByCategoria(Long torneoCategoriaId, Pageable pageable) {
        requireCategoria(torneoCategoriaId);
        return repository.findByTorneoCategoriaId(torneoCategoriaId, pageable).map(this::toResponse);
    }

    public InscripcionEquipoResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public InscripcionEquipoResponseDto create(InscripcionEquipoRequestDto request) {
        InscripcionEquipo entity = new InscripcionEquipo();
        apply(entity, request);
        entity.setEstado(EstadoInscripcion.PENDIENTE);
        entity.setListaConfirmada(false);
        entity.setFechaInscripcion(LocalDateTime.now());
        validarVentanaDeInscripcion(entity.getTorneoCategoria());
        validarCupo(entity.getTorneoCategoria());
        validate(entity);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public InscripcionEquipoResponseDto update(Long id, InscripcionEquipoRequestDto request) {
        InscripcionEquipo entity = require(id);
        apply(entity, request);
        validate(entity);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private InscripcionEquipo require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Inscripción con id " + id + " no existe"));
    }

    private TorneoCategoria requireCategoria(Long torneoCategoriaId) {
        return categorias.findById(torneoCategoriaId)
                .orElseThrow(() -> new NotFoundException(
                        "La categoría con id " + torneoCategoriaId + " no existe"));
    }

    private void apply(InscripcionEquipo entity, InscripcionEquipoRequestDto request) {
        entity.setTorneoCategoria(requireCategoria(request.torneoCategoriaId()));
        entity.setClub(relations.find(Club.class, request.clubId()));
        entity.setUsuario(relations.find(Usuario.class, request.usuarioId()));
        entity.setGrupo(relations.find(Grupo.class, request.grupoId()));
        entity.setCabezaSerie(request.cabezaSerie());
    }

    /** HU 4: solo con inscripciones abiertas y antes de la fecha límite. */
    private void validarVentanaDeInscripcion(TorneoCategoria categoria) {
        Torneo torneo = categoria.getTorneo();
        if (torneo.getEstado() != EstadoTorneo.INSCRIPCIONES_ABIERTAS) {
            throw new BusinessException("El torneo " + torneo.getNombre()
                    + " no tiene las inscripciones abiertas; está en " + torneo.getEstado());
        }
        if (LocalDate.now().isAfter(torneo.getFechaLimiteInscripcion())) {
            throw new BusinessException("La fecha límite de inscripción del torneo "
                    + torneo.getNombre() + " fue el " + torneo.getFechaLimiteInscripcion());
        }
    }

    /** Tarea S1-21: respetar el cupo máximo de la categoría. */
    private void validarCupo(TorneoCategoria categoria) {
        long inscritos = repository.countByTorneoCategoriaId(categoria.getId());
        if (inscritos >= categoria.getCupoMaximo()) {
            throw new BusinessException("La categoría " + categoria.getNombre()
                    + " ya llegó a su cupo de " + categoria.getCupoMaximo() + " equipos");
        }
    }

    /** Tarea S1-21: un club una sola vez por categoría, y el grupo debe ser de esa categoría. */
    private void validate(InscripcionEquipo entity) {
        repository.findByTorneoCategoriaIdAndClubId(
                        entity.getTorneoCategoria().getId(), entity.getClub().getId())
                .filter(existing -> !Objects.equals(existing.getId(), entity.getId()))
                .ifPresent(existing -> {
                    throw new ConflictException("El club ya está inscrito en la categoría "
                            + entity.getTorneoCategoria().getNombre());
                });
        Grupo grupo = entity.getGrupo();
        if (grupo != null && !Objects.equals(
                grupo.getTorneoCategoria().getId(), entity.getTorneoCategoria().getId())) {
            throw new BusinessException("El grupo " + grupo.getNombre()
                    + " no pertenece a la categoría de la inscripción");
        }
    }

    private InscripcionEquipoResponseDto toResponse(InscripcionEquipo entity) {
        return new InscripcionEquipoResponseDto(
                entity.getId(),
                relations.id(entity.getTorneoCategoria()),
                relations.id(entity.getClub()),
                relations.id(entity.getGrupo()),
                entity.getCabezaSerie(),
                entity.getEstado(),
                entity.getMotivo(),
                entity.getListaConfirmada(),
                relations.id(entity.getUsuario()),
                entity.getFechaInscripcion());
    }
}

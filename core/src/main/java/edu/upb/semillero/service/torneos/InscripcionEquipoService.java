package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.CambioEstadoInscripcionRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.InscripcionEquipoRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.AuditoriaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.InscripcionEquipoResponseDto;
import edu.upb.semillero.repository.entity.Auditoria;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.torneos.Grupo;
import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.torneos.EstadoInscripcion;
import edu.upb.semillero.repository.enums.torneos.EstadoTorneo;
import edu.upb.semillero.repository.repos.AuditoriaRepository;
import edu.upb.semillero.repository.repos.torneos.InscripcionEquipoRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoTorneoRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 4 - Inscribir un equipo (tareas S1-20 y S1-21) y HU 6 - Aprobar o
 * rechazar inscripciones (tareas S2-10, S2-11 y S2-12).
 *
 * <p>La inscripción nace PENDIENTE y solo el organizador la mueve, siempre con
 * motivo si la observa o la rechaza. Cada cambio queda en {@code auditoria} con
 * el usuario y la fecha, y se consulta en
 * {@code GET /api/v1/torneos/inscripciones/&#123;id&#125;/historial}.
 */
@Service
@Transactional(readOnly = true)
public class InscripcionEquipoService {
    private static final String MODULO = "TORNEOS";
    private static final String TABLA = "inscripciones_equipo";

    private final InscripcionEquipoRepository repository;
    private final TorneoCategoriaRepository categorias;
    private final AuditoriaRepository auditoria;
    private final PartidoTorneoRepository partidos;
    private final EntityRelations relations;

    public InscripcionEquipoService(InscripcionEquipoRepository repository,
            TorneoCategoriaRepository categorias, AuditoriaRepository auditoria,
            PartidoTorneoRepository partidos, EntityRelations relations) {
        this.repository = repository;
        this.categorias = categorias;
        this.auditoria = auditoria;
        this.partidos = partidos;
        this.relations = relations;
    }

    /**
     * Tarea S2-10: el listado que revisa el organizador. Se puede filtrar por
     * torneo, por categoría y por estado, y se combinan entre sí.
     */
    public Page<InscripcionEquipoResponseDto> findAll(Long torneoId, Long torneoCategoriaId,
            EstadoInscripcion estado, Pageable pageable) {
        return buscar(torneoId, torneoCategoriaId, estado, pageable).map(this::toResponse);
    }

    public InscripcionEquipoResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    /** Tarea S2-12: el historial de estados, del más reciente al más antiguo. */
    public List<AuditoriaResponseDto> findHistorial(Long id) {
        require(id);
        return auditoria.findByTablaAndRegistroIdOrderByFechaDescIdDesc(TABLA, id).stream()
                .map(this::toAuditoriaResponse)
                .toList();
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

    /**
     * Tarea S2-11: aprobar, observar o rechazar. Observar y rechazar piden
     * motivo, que es lo que después ve el entrenador. Aprobar lo limpia.
     */
    @Transactional
    public InscripcionEquipoResponseDto cambiarEstado(Long id,
            CambioEstadoInscripcionRequestDto request) {
        InscripcionEquipo entity = require(id);
        EstadoInscripcion anterior = entity.getEstado();
        EstadoInscripcion nuevo = request.estado();
        if (anterior == nuevo) {
            throw new BusinessException("La inscripción ya está en estado " + nuevo);
        }
        if (exigeMotivo(nuevo) && (request.motivo() == null || request.motivo().isBlank())) {
            throw new BusinessException("Para dejar la inscripción en " + nuevo
                    + " hay que indicar el motivo");
        }
        if (anterior == EstadoInscripcion.APROBADA && hayFixture(entity)) {
            throw new ConflictException("La categoría " + entity.getTorneoCategoria().getNombre()
                    + " ya tiene fixture generado: no se puede sacar a un equipo aprobado");
        }
        String valorAnterior = snapshot(entity);
        entity.setEstado(nuevo);
        entity.setMotivo(exigeMotivo(nuevo) ? request.motivo() : null);
        InscripcionEquipo guardada = repository.saveAndFlush(entity);
        registrar(guardada, valorAnterior, snapshot(guardada), request.motivo(), request.usuarioId());
        return toResponse(guardada);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Page<InscripcionEquipo> buscar(Long torneoId, Long torneoCategoriaId,
            EstadoInscripcion estado, Pageable pageable) {
        if (torneoCategoriaId != null) {
            requireCategoria(torneoCategoriaId);
            return estado == null
                    ? repository.findByTorneoCategoriaId(torneoCategoriaId, pageable)
                    : repository.findByTorneoCategoriaIdAndEstado(torneoCategoriaId, estado, pageable);
        }
        if (torneoId != null) {
            return estado == null
                    ? repository.findByTorneoCategoriaTorneoId(torneoId, pageable)
                    : repository.findByTorneoCategoriaTorneoIdAndEstado(torneoId, estado, pageable);
        }
        return estado == null ? repository.findAll(pageable) : repository.findByEstado(estado, pageable);
    }

    private boolean exigeMotivo(EstadoInscripcion estado) {
        return estado == EstadoInscripcion.OBSERVADA || estado == EstadoInscripcion.RECHAZADA;
    }

    private boolean hayFixture(InscripcionEquipo entity) {
        return partidos.countByTorneoCategoriaId(entity.getTorneoCategoria().getId()) > 0;
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

    private String snapshot(InscripcionEquipo entity) {
        String motivo = entity.getMotivo() == null ? "null" : "\"" + entity.getMotivo() + "\"";
        return "{\"estado\":\"%s\",\"motivo\":%s,\"listaConfirmada\":%s}"
                .formatted(entity.getEstado(), motivo, entity.getListaConfirmada());
    }

    private void registrar(InscripcionEquipo entity, String anterior, String nuevo, String motivo,
            Long usuarioId) {
        Auditoria fila = new Auditoria();
        fila.setModulo(MODULO);
        fila.setTabla(TABLA);
        fila.setRegistroId(entity.getId());
        fila.setAccion("CAMBIO_ESTADO");
        fila.setValorAnterior(anterior);
        fila.setValorNuevo(nuevo);
        fila.setMotivo(motivo);
        fila.setUsuario(relations.find(Usuario.class, usuarioId));
        fila.setFecha(LocalDateTime.now());
        auditoria.saveAndFlush(fila);
    }

    private AuditoriaResponseDto toAuditoriaResponse(Auditoria entity) {
        return new AuditoriaResponseDto(
                entity.getId(),
                entity.getModulo(),
                entity.getTabla(),
                entity.getRegistroId(),
                entity.getAccion(),
                entity.getValorAnterior(),
                entity.getValorNuevo(),
                entity.getMotivo(),
                relations.id(entity.getUsuario()),
                entity.getFecha());
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

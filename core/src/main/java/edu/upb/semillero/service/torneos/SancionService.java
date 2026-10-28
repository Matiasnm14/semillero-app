package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.ApelacionRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.ResolucionApelacionRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.SancionManualRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.ApelacionResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.AuditoriaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.SancionResponseDto;
import edu.upb.semillero.repository.entity.Auditoria;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.torneos.Apelacion;
import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import edu.upb.semillero.repository.entity.torneos.ListaBuenaFe;
import edu.upb.semillero.repository.entity.torneos.Sancion;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.torneos.EstadoApelacion;
import edu.upb.semillero.repository.enums.torneos.EstadoSancion;
import edu.upb.semillero.repository.enums.torneos.OrigenSancion;
import edu.upb.semillero.repository.repos.AuditoriaRepository;
import edu.upb.semillero.repository.repos.torneos.ApelacionRepository;
import edu.upb.semillero.repository.repos.torneos.ListaBuenaFeRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoFixtureRepository;
import edu.upb.semillero.repository.repos.torneos.SancionRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import edu.upb.semillero.service.seguridad.AccesoTorneos;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 13 - Suspensiones: consulta, lista de sancionados, sanción manual y
 * apelación (tareas S4-04 y S4-05). Las automáticas las genera el cierre del
 * partido con {@link SancionesAutomaticas} (S4-01 a S4-03), y el bloqueo en la
 * planilla lo aplica {@code PlanillaService} con las sanciones VIGENTE.
 *
 * <ul>
 *   <li><strong>S4-04</strong>: la lista de sancionados del torneo, y la de
 *       una fecha del fixture: los que no pueden jugar esa jornada.</li>
 *   <li><strong>S4-05</strong>: el organizador pone una sanción manual con
 *       motivo. El entrenador apela una sanción vigente de su equipo, una
 *       apelación pendiente a la vez, y el organizador la resuelve: aceptada
 *       anula la sanción, rechazada la deja igual y reducida le baja las
 *       fechas.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class SancionService {
    private static final String MODULO = "TORNEOS";
    private static final String TABLA = "sanciones";

    private final SancionRepository repository;
    private final ApelacionRepository apelaciones;
    private final ListaBuenaFeRepository listas;
    private final TorneoRepository torneos;
    private final TorneoCategoriaRepository categorias;
    private final PartidoFixtureRepository partidos;
    private final AuditoriaRepository auditoria;
    private final AvisosTorneos avisos;
    private final AccesoTorneos acceso;
    private final EntityRelations relations;

    public SancionService(SancionRepository repository, ApelacionRepository apelaciones,
            ListaBuenaFeRepository listas, TorneoRepository torneos, TorneoCategoriaRepository categorias,
            PartidoFixtureRepository partidos, AuditoriaRepository auditoria, AvisosTorneos avisos,
            AccesoTorneos acceso, EntityRelations relations) {
        this.repository = repository;
        this.apelaciones = apelaciones;
        this.listas = listas;
        this.torneos = torneos;
        this.categorias = categorias;
        this.partidos = partidos;
        this.auditoria = auditoria;
        this.avisos = avisos;
        this.acceso = acceso;
        this.relations = relations;
    }

    /** Tarea S4-04: las sanciones del torneo, filtradas por estado y por categoría. */
    public List<SancionResponseDto> delTorneo(Long torneoId, EstadoSancion estado, Long categoriaId) {
        requireTorneo(torneoId);
        Map<Long, List<Partido>> fixtures = new HashMap<>();
        return repository.findByListaBuenaFeTorneoIdOrderByFechaRegistroDescIdDesc(torneoId).stream()
                .filter(s -> estado == null || s.getEstado() == estado)
                .filter(s -> categoriaId == null
                        || Objects.equals(categoriaDe(s).getId(), categoriaId))
                .map(s -> toResponse(s, fixtures))
                .toList();
    }

    /**
     * Tarea S4-04: los sancionados de una fecha: los que tienen una sanción
     * (no anulada) que cubre la jornada {@code jornada} de su equipo en la
     * categoría. Sirve para la fecha que viene y para revisar una que pasó.
     */
    public List<SancionResponseDto> sancionadosDeLaFecha(Long torneoId, Long categoriaId, Short jornada) {
        categorias.findByIdAndTorneoId(categoriaId, torneoId)
                .orElseThrow(() -> new NotFoundException("La categoría con id " + categoriaId
                        + " no existe en el torneo " + torneoId));
        Map<Long, List<Partido>> fixtures = new HashMap<>();
        return repository.findByListaBuenaFeTorneoIdOrderByFechaRegistroDescIdDesc(torneoId).stream()
                .filter(s -> s.getEstado() != EstadoSancion.ANULADA)
                .filter(s -> Objects.equals(categoriaDe(s).getId(), categoriaId))
                .map(s -> toResponse(s, fixtures))
                .filter(s -> s.jornadasSuspendido().contains(jornada))
                .sorted(Comparator.comparing(SancionResponseDto::clubId)
                        .thenComparing(SancionResponseDto::jugadorId))
                .toList();
    }

    /** HU 14 y 15: las sanciones de un jugador; con {@code soloVigentes}, el aviso de la app. */
    public List<SancionResponseDto> delJugador(Long jugadorId, boolean soloVigentes) {
        Map<Long, List<Partido>> fixtures = new HashMap<>();
        return repository.findByListaBuenaFeJugadorIdOrderByFechaRegistroDescIdDesc(jugadorId).stream()
                .filter(s -> !soloVigentes || s.getEstado() == EstadoSancion.VIGENTE)
                .map(s -> toResponse(s, fixtures))
                .toList();
    }

    public SancionResponseDto findById(Long id) {
        return toResponse(require(id), new HashMap<>());
    }

    /** La historia de una sanción: alta, fechas descontadas, apelaciones y anulación. */
    public List<AuditoriaResponseDto> historial(Long id) {
        require(id);
        return auditoria.findByTablaAndRegistroIdOrderByFechaDescIdDesc(TABLA, id).stream()
                .map(this::toAuditoriaResponse)
                .toList();
    }

    /** Tarea S4-05: sanción manual del organizador, con motivo. */
    @Transactional
    public SancionResponseDto crearManual(Long torneoId, SancionManualRequestDto request) {
        Torneo torneo = requireTorneo(torneoId);
        ListaBuenaFe lista = listas.findByTorneoIdAndJugadorId(torneoId, request.jugadorId())
                .orElseThrow(() -> new BusinessException("El jugador " + request.jugadorId()
                        + " no está en ninguna lista de buena fe del torneo " + torneo.getNombre()));
        Partido partido = null;
        if (request.partidoId() != null) {
            partido = partidos.findById(request.partidoId())
                    .orElseThrow(() -> new NotFoundException("Partido con id " + request.partidoId()
                            + " no existe"));
            if (partido.getTorneoCategoria() == null
                    || !Objects.equals(partido.getTorneoCategoria().getTorneo().getId(), torneoId)) {
                throw new BusinessException("El partido " + request.partidoId()
                        + " no es del torneo " + torneo.getNombre());
            }
        }
        Sancion sancion = new Sancion();
        sancion.setListaBuenaFe(lista);
        sancion.setPartido(partido);
        sancion.setOrigen(OrigenSancion.MANUAL);
        sancion.setFechasSuspension(request.fechasSuspension());
        sancion.setFechasCumplidas((short) 0);
        sancion.setMotivo(request.motivo().trim());
        sancion.setEstado(EstadoSancion.VIGENTE);
        sancion.setUsuario(relations.find(Usuario.class, request.usuarioId()));
        sancion.setFechaRegistro(LocalDateTime.now());
        Sancion guardada = repository.saveAndFlush(sancion);
        registrar("SANCION_MANUAL", guardada, null, guardada.getMotivo(), request.usuarioId());
        avisos.avisarAlJugador(lista.getJugador(), "Suspensión",
                "Tenés " + guardada.getFechasSuspension() + " fecha(s) de suspensión en el torneo "
                        + torneo.getNombre() + ". " + guardada.getMotivo());
        return toResponse(guardada, new HashMap<>());
    }

    public List<ApelacionResponseDto> apelacionesDe(Long sancionId) {
        require(sancionId);
        return apelaciones.findBySancionIdOrderByIdAsc(sancionId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Tarea S4-05: el entrenador apela una sanción vigente de un jugador de su
     * club; el organizador también puede. Una apelación pendiente a la vez.
     */
    @Transactional
    public ApelacionResponseDto apelar(Long sancionId, ApelacionRequestDto request) {
        Sancion sancion = require(sancionId);
        acceso.requireClubPropio(sancion.getListaBuenaFe().getInscripcionEquipo().getClub().getId(),
                "/api/v1/torneos/sanciones/" + sancionId + "/apelaciones");
        if (sancion.getEstado() != EstadoSancion.VIGENTE) {
            throw new BusinessException("La sanción " + sancionId + " está " + sancion.getEstado()
                    + ": solo se apela una sanción VIGENTE");
        }
        if (apelaciones.existsBySancionIdAndEstado(sancionId, EstadoApelacion.PENDIENTE)) {
            throw new ConflictException("La sanción " + sancionId
                    + " ya tiene una apelación PENDIENTE");
        }
        Apelacion apelacion = new Apelacion();
        apelacion.setSancion(sancion);
        apelacion.setMotivo(request.motivo().trim());
        apelacion.setEstado(EstadoApelacion.PENDIENTE);
        apelacion.setUsuario(relations.find(Usuario.class, request.usuarioId()));
        apelacion.setFechaPresentacion(LocalDateTime.now());
        Apelacion guardada = apelaciones.saveAndFlush(apelacion);
        registrar("APELAR", sancion, null, guardada.getMotivo(), request.usuarioId());
        return toResponse(guardada);
    }

    /** Tarea S4-05: el organizador resuelve la apelación, con la resolución por escrito. */
    @Transactional
    public ApelacionResponseDto resolver(Long apelacionId, ResolucionApelacionRequestDto request) {
        Apelacion apelacion = apelaciones.findById(apelacionId)
                .orElseThrow(() -> new NotFoundException("Apelación con id " + apelacionId + " no existe"));
        if (apelacion.getEstado() != EstadoApelacion.PENDIENTE) {
            throw new ConflictException("La apelación " + apelacionId + " ya está resuelta: "
                    + apelacion.getEstado());
        }
        if (request.estado() == EstadoApelacion.PENDIENTE) {
            throw new BusinessException("Para resolver la apelación hay que aceptarla, rechazarla o"
                    + " reducir la sanción");
        }
        Sancion sancion = apelacion.getSancion();
        String anterior = SancionesAutomaticas.snapshot(sancion);
        switch (request.estado()) {
            case ACEPTADA -> sancion.setEstado(EstadoSancion.ANULADA);
            case REDUCIDA -> reducir(sancion, request.fechasResueltas());
            default -> {
                if (request.fechasResueltas() != null) {
                    throw new BusinessException("Una apelación rechazada no cambia las fechas");
                }
            }
        }
        repository.saveAndFlush(sancion);
        apelacion.setEstado(request.estado());
        apelacion.setResolucion(request.resolucion().trim());
        apelacion.setFechasResueltas(request.estado() == EstadoApelacion.REDUCIDA
                ? request.fechasResueltas() : null);
        apelacion.setResueltoPor(relations.find(Usuario.class, request.usuarioId()));
        apelacion.setFechaResolucion(LocalDateTime.now());
        Apelacion resuelta = apelaciones.saveAndFlush(apelacion);
        registrar("RESOLVER_APELACION", sancion, anterior,
                request.estado() + ": " + resuelta.getResolucion(), request.usuarioId());
        return toResponse(resuelta);
    }

    /** Reducida: menos fechas que las que tenía; si ya las cumplió, queda CUMPLIDA. */
    private void reducir(Sancion sancion, Short fechas) {
        if (fechas == null) {
            throw new BusinessException("Para reducir la sanción hay que indicar fechasResueltas");
        }
        if (fechas >= sancion.getFechasSuspension()) {
            throw new BusinessException("La sanción tiene " + sancion.getFechasSuspension()
                    + " fecha(s): reducirla es dejarle menos");
        }
        sancion.setFechasSuspension(fechas);
        if (sancion.getFechasCumplidas() >= fechas) {
            sancion.setFechasCumplidas(fechas);
            sancion.setEstado(EstadoSancion.CUMPLIDA);
        }
    }

    private Sancion require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sanción con id " + id + " no existe"));
    }

    private Torneo requireTorneo(Long torneoId) {
        return torneos.findById(torneoId)
                .orElseThrow(() -> new NotFoundException("Torneo con id " + torneoId + " no existe"));
    }

    private TorneoCategoria categoriaDe(Sancion sancion) {
        return sancion.getListaBuenaFe().getInscripcionEquipo().getTorneoCategoria();
    }

    /**
     * Tarea S4-04: las jornadas que cubre la sanción. Son los primeros
     * {@code fechas_suspension} partidos del equipo, no anulados, posteriores
     * al que la originó (o al alta, si es manual y sin partido).
     */
    private List<Short> jornadasSuspendido(Sancion sancion, Map<Long, List<Partido>> fixtures) {
        if (sancion.getEstado() == EstadoSancion.ANULADA) {
            return List.of();
        }
        InscripcionEquipo inscripcion = sancion.getListaBuenaFe().getInscripcionEquipo();
        Long clubId = inscripcion.getClub().getId();
        Partido origen = sancion.getPartido();
        LocalDateTime desde = origen != null ? origen.getFechaInicio() : sancion.getFechaRegistro();
        return fixtures.computeIfAbsent(inscripcion.getTorneoCategoria().getId(),
                        partidos::findByTorneoCategoriaIdOrderByJornadaAscFechaInicioAsc).stream()
                .filter(p -> p.getEstado() != EstadoPartido.CANCELADO)
                .filter(p -> Objects.equals(p.getClubLocal().getId(), clubId)
                        || Objects.equals(p.getClubVisitante().getId(), clubId))
                .filter(p -> origen == null || !Objects.equals(p.getId(), origen.getId()))
                .filter(p -> p.getFechaInicio().isAfter(desde))
                .sorted(Comparator.comparing(Partido::getFechaInicio))
                .limit(sancion.getFechasSuspension())
                .map(Partido::getJornada)
                .filter(Objects::nonNull)
                .toList();
    }

    private void registrar(String accion, Sancion sancion, String anterior, String motivo,
            Long usuarioId) {
        Auditoria fila = new Auditoria();
        fila.setModulo(MODULO);
        fila.setTabla(TABLA);
        fila.setRegistroId(sancion.getId());
        fila.setAccion(accion);
        fila.setValorAnterior(anterior);
        fila.setValorNuevo(SancionesAutomaticas.snapshot(sancion));
        fila.setMotivo(motivo == null || motivo.length() <= 255 ? motivo : motivo.substring(0, 255));
        fila.setUsuario(relations.find(Usuario.class, usuarioId));
        fila.setFecha(LocalDateTime.now());
        auditoria.saveAndFlush(fila);
    }

    private SancionResponseDto toResponse(Sancion entity, Map<Long, List<Partido>> fixtures) {
        ListaBuenaFe lista = entity.getListaBuenaFe();
        InscripcionEquipo inscripcion = lista.getInscripcionEquipo();
        Partido origen = entity.getPartido();
        return new SancionResponseDto(
                entity.getId(),
                relations.id(lista.getTorneo()),
                relations.id(inscripcion.getTorneoCategoria()),
                inscripcion.getId(),
                relations.id(inscripcion.getClub()),
                relations.id(lista.getJugador()),
                lista.getId(),
                relations.id(origen),
                origen == null ? null : origen.getJornada(),
                entity.getOrigen(),
                entity.getFechasSuspension(),
                entity.getFechasCumplidas(),
                entity.getEstado() == EstadoSancion.VIGENTE
                        ? entity.getFechasSuspension() - entity.getFechasCumplidas() : 0,
                entity.getMotivo(),
                entity.getEstado(),
                apelaciones.existsBySancionIdAndEstado(entity.getId(), EstadoApelacion.PENDIENTE),
                jornadasSuspendido(entity, fixtures),
                relations.id(entity.getUsuario()),
                entity.getFechaRegistro());
    }

    private ApelacionResponseDto toResponse(Apelacion entity) {
        return new ApelacionResponseDto(
                entity.getId(),
                relations.id(entity.getSancion()),
                entity.getMotivo(),
                entity.getEstado(),
                entity.getResolucion(),
                entity.getFechasResueltas(),
                relations.id(entity.getUsuario()),
                relations.id(entity.getResueltoPor()),
                entity.getFechaPresentacion(),
                entity.getFechaResolucion());
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
}

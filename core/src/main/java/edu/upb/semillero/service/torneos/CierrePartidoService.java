package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.CierrePartidoRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.ReaperturaPartidoRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.AuditoriaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.MinutosJugadorResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.ResultadoPartidoResponseDto;
import edu.upb.semillero.repository.entity.Auditoria;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.ParticipacionPartido;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.torneos.EventoPartido;
import edu.upb.semillero.repository.entity.torneos.Planilla;
import edu.upb.semillero.repository.entity.torneos.Reglamento;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.FaseTorneo;
import edu.upb.semillero.repository.enums.torneos.AmbitoTorneo;
import edu.upb.semillero.repository.enums.torneos.EstadoPlanilla;
import edu.upb.semillero.repository.enums.torneos.TipoEventoPartido;
import edu.upb.semillero.repository.repos.AuditoriaRepository;
import edu.upb.semillero.repository.repos.torneos.EventoPartidoRepository;
import edu.upb.semillero.repository.repos.torneos.ParticipacionPlanillaRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoFixtureRepository;
import edu.upb.semillero.repository.repos.torneos.PlanillaRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import edu.upb.semillero.service.seguridad.AccesoTorneos;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 10 - Cerrar y reabrir el partido (tareas S3-08, S3-09 y S3-10).
 *
 * <ul>
 *   <li><strong>S3-08</strong>: los minutos de cada jugador salen de la
 *       planilla y de los cambios y expulsiones, y se guardan en
 *       {@code participaciones_partido} ({@link MinutosJugados}).</li>
 *   <li><strong>S3-09</strong>: el cierre es una sola transacción (regla 7):
 *       pone FINALIZADO, guarda goles y minutos, y llena {@code fecha_cierre} y
 *       {@code cerrado_por}. Si algo falla, no queda nada a medias. La tabla y
 *       las estadísticas no se guardan: se calculan desde los partidos
 *       cerrados (regla 5), así que quedan al día en cuanto se cierra.</li>
 *   <li><strong>S3-10</strong>: solo el organizador reabre un partido cerrado,
 *       con motivo, y queda en {@code auditoria}.</li>
 * </ul>
 *
 * <p>Dentro de esta misma transacción se van a sumar las sanciones por
 * tarjetas (tareas S4-01 y S4-02) y el aviso {@code PartidoFinalizado} al
 * microservicio de video (tarea S4-09). Mientras tanto, el microservicio puede
 * leer los partidos FINALIZADO por {@code fecha_cierre} (punto 13 del acuerdo).
 */
@Service
@Transactional(readOnly = true)
public class CierrePartidoService {
    private static final String MODULO = "TORNEOS";
    private static final String TABLA = "partidos";
    private static final Set<String> ACCIONES = Set.of("CERRAR", "REABRIR");
    /** Goles de W.O. si el torneo no tiene reglamento: el valor por defecto de la columna. */
    private static final short GOLES_WALKOVER = 3;

    private final PartidoFixtureRepository partidos;
    private final PlanillaRepository planillas;
    private final ParticipacionPlanillaRepository participaciones;
    private final EventoPartidoRepository eventos;
    private final ReglamentoRepository reglamentos;
    private final AuditoriaRepository auditoria;
    private final AccesoTorneos acceso;
    private final EntityRelations relations;

    public CierrePartidoService(PartidoFixtureRepository partidos, PlanillaRepository planillas,
            ParticipacionPlanillaRepository participaciones, EventoPartidoRepository eventos,
            ReglamentoRepository reglamentos, AuditoriaRepository auditoria, AccesoTorneos acceso,
            EntityRelations relations) {
        this.partidos = partidos;
        this.planillas = planillas;
        this.participaciones = participaciones;
        this.eventos = eventos;
        this.reglamentos = reglamentos;
        this.auditoria = auditoria;
        this.acceso = acceso;
        this.relations = relations;
    }

    /** El resultado como está ahora, cerrado o no. */
    public ResultadoPartidoResponseDto resultado(Long partidoId) {
        return toResponse(requirePartido(partidoId));
    }

    /** Tarea S3-10: los cierres y reaperturas del partido, del más reciente al más antiguo. */
    public List<AuditoriaResponseDto> historial(Long partidoId) {
        requirePartido(partidoId);
        return auditoria.findByTablaAndRegistroIdOrderByFechaDescIdDesc(TABLA, partidoId).stream()
                .filter(fila -> ACCIONES.contains(fila.getAccion()))
                .map(this::toAuditoriaResponse)
                .toList();
    }

    /** Tareas S3-08 y S3-09: cierra el partido en una sola transacción. */
    @Transactional
    public ResultadoPartidoResponseDto cerrar(Long partidoId, CierrePartidoRequestDto request) {
        Partido partido = requirePartido(partidoId);
        acceso.requirePartidoAsignado(partidoId, relations.id(partido.getMesaControl()));
        if (partido.getEstado() == EstadoPartido.FINALIZADO) {
            throw new ConflictException("El partido " + partidoId + " ya está cerrado");
        }
        if (partido.getEstado() == EstadoPartido.CANCELADO) {
            throw new ConflictException("El partido " + partidoId
                    + " está anulado (CANCELADO): no se cierra");
        }
        String anterior = snapshot(partido);
        List<EventoPartido> delPartido = eventos.findByPartidoIdOrderByMinutoAscIdAsc(partidoId);

        if (request.ganadorWalkoverId() != null) {
            cerrarPorWalkover(partido, request, delPartido);
        } else {
            cerrarJugado(partido, request, delPartido);
        }
        partido.setEstado(EstadoPartido.FINALIZADO);
        partido.setFechaCierre(LocalDateTime.now());
        partido.setCerradoPor(relations.find(Usuario.class, request.usuarioId()));
        Partido cerrado = partidos.saveAndFlush(partido);
        registrar("CERRAR", cerrado, anterior, null, request.usuarioId());
        return toResponse(cerrado);
    }

    /**
     * Tarea S3-10: reabre un partido cerrado. Vuelve a EN_CURSO (o a PROGRAMADO
     * si se había cerrado por W.O.), se borran el resultado y los minutos, y
     * hay que cerrarlo otra vez. No se reabre si la ronda siguiente de las
     * llaves ya se armó con su ganador.
     */
    @Transactional
    public ResultadoPartidoResponseDto reabrir(Long partidoId, ReaperturaPartidoRequestDto request) {
        Partido partido = requirePartido(partidoId);
        if (partido.getEstado() != EstadoPartido.FINALIZADO) {
            throw new ConflictException("El partido " + partidoId + " está en " + partido.getEstado()
                    + ": solo se reabre un partido cerrado");
        }
        if (partido.getPartidoSiguiente() != null) {
            throw new ConflictException("El ganador del partido " + partidoId
                    + " ya juega el partido " + partido.getPartidoSiguiente().getId()
                    + " de la ronda siguiente: no se puede reabrir");
        }
        String anterior = snapshot(partido);
        boolean eraWalkover = partido.getGanadorWalkover() != null;
        partido.setEstado(eraWalkover ? EstadoPartido.PROGRAMADO : EstadoPartido.EN_CURSO);
        partido.setGolesLocal(null);
        partido.setGolesVisitante(null);
        partido.setPenalesLocal(null);
        partido.setPenalesVisitante(null);
        partido.setGanadorWalkover(null);
        partido.setFechaCierre(null);
        partido.setCerradoPor(null);
        for (ParticipacionPartido participacion : participaciones.findByPartidoIdOrderByIdAsc(partidoId)) {
            participacion.setMinutosJugados(null);
            participaciones.save(participacion);
        }
        participaciones.flush();
        Partido reabierto = partidos.saveAndFlush(partido);
        registrar("REABRIR", reabierto, anterior, request.motivo().trim(), request.usuarioId());
        return toResponse(reabierto);
    }

    private Partido requirePartido(Long partidoId) {
        Partido partido = partidos.findById(partidoId)
                .orElseThrow(() -> new NotFoundException("Partido con id " + partidoId + " no existe"));
        if (partido.getTorneoCategoria() == null) {
            throw new BusinessException("El partido " + partidoId
                    + " no es de un torneo: se cierra desde el CRUD de partidos");
        }
        return partido;
    }

    /** W.O.: no se jugó, el ganador se lleva los goles de W.O. del reglamento. */
    private void cerrarPorWalkover(Partido partido, CierrePartidoRequestDto request,
            List<EventoPartido> delPartido) {
        Club ganador = clubDelPartido(partido, request.ganadorWalkoverId());
        if (!delPartido.isEmpty()) {
            throw new ConflictException("El partido " + partido.getId()
                    + " tiene eventos cargados: se jugó y no se puede cerrar por W.O.");
        }
        if (request.golesLocal() != null || request.golesVisitante() != null
                || request.penalesLocal() != null || request.penalesVisitante() != null) {
            throw new BusinessException("Un partido por W.O. no lleva goles ni penales: los pone"
                    + " el reglamento");
        }
        short goles = reglamento(partido).map(Reglamento::getGolesWalkover).orElse(GOLES_WALKOVER);
        boolean ganaLocal = Objects.equals(ganador.getId(), partido.getClubLocal().getId());
        partido.setGanadorWalkover(ganador);
        partido.setGolesLocal(ganaLocal ? goles : 0);
        partido.setGolesVisitante(ganaLocal ? 0 : goles);
        partido.setPenalesLocal(null);
        partido.setPenalesVisitante(null);
        observaciones(partido, request);
    }

    private void cerrarJugado(Partido partido, CierrePartidoRequestDto request,
            List<EventoPartido> delPartido) {
        Planilla planilla = planillas.findByPartidoId(partido.getId())
                .orElseThrow(() -> new BusinessException("El partido " + partido.getId()
                        + " no tiene planilla: no se puede cerrar"));
        if (planilla.getEstado() != EstadoPlanilla.CONFIRMADA) {
            throw new BusinessException("La planilla del partido " + partido.getId()
                    + " no está confirmada: no se puede cerrar");
        }
        short local = golesDeEventos(delPartido, partido.getClubLocal());
        short visitante = golesDeEventos(delPartido, partido.getClubVisitante());
        if (partido.getTorneoCategoria().getTorneo().getAmbito() == AmbitoTorneo.EXTERNO) {
            // El rival de un campeonato externo no tiene planilla: sus goles no son eventos.
            if (request.golesLocal() == null || request.golesVisitante() == null) {
                throw new BusinessException("En un campeonato externo el resultado se carga a mano:"
                        + " faltan golesLocal y golesVisitante");
            }
            if (request.golesLocal() < local || request.golesVisitante() < visitante) {
                throw new BusinessException("El resultado " + request.golesLocal() + "-"
                        + request.golesVisitante() + " tiene menos goles que los eventos cargados ("
                        + local + "-" + visitante + ")");
            }
            local = request.golesLocal();
            visitante = request.golesVisitante();
        } else if ((request.golesLocal() != null && request.golesLocal() != local)
                || (request.golesVisitante() != null && request.golesVisitante() != visitante)) {
            throw new BusinessException("Los goles salen de los eventos y dan " + local + "-"
                    + visitante + ": cargá o borrá los goles que falten");
        }
        partido.setGolesLocal(local);
        partido.setGolesVisitante(visitante);
        partido.setGanadorWalkover(null);
        penales(partido, request, local == visitante);
        guardarMinutos(partido, delPartido);
        observaciones(partido, request);
    }

    /** Los penales solo definen un partido de eliminación empatado, y ahí no pueden faltar. */
    private void penales(Partido partido, CierrePartidoRequestDto request, boolean empate) {
        boolean vienen = request.penalesLocal() != null || request.penalesVisitante() != null;
        boolean eliminacion = partido.getFase() != FaseTorneo.LIGA && partido.getFase() != FaseTorneo.GRUPOS;
        if (!eliminacion || !empate) {
            if (vienen) {
                throw new BusinessException("Los penales solo van en un partido de eliminación que"
                        + " terminó empatado");
            }
            partido.setPenalesLocal(null);
            partido.setPenalesVisitante(null);
            return;
        }
        if (request.penalesLocal() == null || request.penalesVisitante() == null) {
            throw new BusinessException("El partido de " + partido.getFase()
                    + " terminó empatado: faltan penalesLocal y penalesVisitante para saber quién pasa");
        }
        if (request.penalesLocal().equals(request.penalesVisitante())) {
            throw new BusinessException("La definición por penales no puede terminar empatada");
        }
        partido.setPenalesLocal(request.penalesLocal());
        partido.setPenalesVisitante(request.penalesVisitante());
    }

    /** Tarea S3-08: entrada, salida y minutos jugados de cada jugador de la planilla. */
    private void guardarMinutos(Partido partido, List<EventoPartido> delPartido) {
        TorneoCategoria categoria = partido.getTorneoCategoria();
        int duracion = categoria.getDuracionTiempoMin() * 2;
        List<ParticipacionPartido> planilla = participaciones.findByPartidoIdOrderByIdAsc(partido.getId());
        Map<Long, MinutosJugados.Tramo> tramos = MinutosJugados.tramos(planilla, delPartido);
        for (ParticipacionPartido participacion : planilla) {
            MinutosJugados.Tramo cerrado = MinutosJugados.cerrado(tramos.get(participacion.getId()),
                    duracion);
            participacion.setMinutoEntrada(cerrado.entrada());
            participacion.setMinutoSalida(cerrado.salida());
            participacion.setMinutosJugados(MinutosJugados.minutos(cerrado, duracion));
            participaciones.save(participacion);
        }
        participaciones.flush();
    }

    private short golesDeEventos(List<EventoPartido> delPartido, Club club) {
        return (short) delPartido.stream()
                .filter(evento -> evento.getTipo() == TipoEventoPartido.GOL)
                .filter(evento -> Objects.equals(evento.getClub().getId(), club.getId()))
                .count();
    }

    private void observaciones(Partido partido, CierrePartidoRequestDto request) {
        if (request.observacionesArbitro() == null) {
            return;
        }
        planillas.findByPartidoId(partido.getId()).ifPresent(planilla -> {
            planilla.setObservacionesArbitro(request.observacionesArbitro());
            planillas.saveAndFlush(planilla);
        });
    }

    private Club clubDelPartido(Partido partido, Long clubId) {
        if (Objects.equals(clubId, partido.getClubLocal().getId())) {
            return partido.getClubLocal();
        }
        if (Objects.equals(clubId, partido.getClubVisitante().getId())) {
            return partido.getClubVisitante();
        }
        throw new BusinessException("El club " + clubId + " no juega el partido " + partido.getId()
                + ": no puede ganarlo por W.O.");
    }

    private Optional<Reglamento> reglamento(Partido partido) {
        return reglamentos.findByTorneoId(partido.getTorneoCategoria().getTorneo().getId());
    }

    private String snapshot(Partido partido) {
        return ("{\"estado\":\"%s\",\"golesLocal\":%s,\"golesVisitante\":%s,\"penalesLocal\":%s,"
                + "\"penalesVisitante\":%s,\"ganadorWalkoverId\":%s,\"fechaCierre\":%s}")
                .formatted(partido.getEstado(), partido.getGolesLocal(), partido.getGolesVisitante(),
                        partido.getPenalesLocal(), partido.getPenalesVisitante(),
                        relations.id(partido.getGanadorWalkover()),
                        partido.getFechaCierre() == null ? "null" : "\"" + partido.getFechaCierre() + "\"");
    }

    private void registrar(String accion, Partido partido, String anterior, String motivo,
            Long usuarioId) {
        Auditoria fila = new Auditoria();
        fila.setModulo(MODULO);
        fila.setTabla(TABLA);
        fila.setRegistroId(partido.getId());
        fila.setAccion(accion);
        fila.setValorAnterior(anterior);
        fila.setValorNuevo(snapshot(partido));
        fila.setMotivo(motivo);
        fila.setUsuario(relations.find(Usuario.class, usuarioId));
        fila.setFecha(LocalDateTime.now());
        auditoria.saveAndFlush(fila);
    }

    private ResultadoPartidoResponseDto toResponse(Partido partido) {
        return new ResultadoPartidoResponseDto(
                partido.getId(),
                partido.getEstado(),
                relations.id(partido.getClubLocal()),
                relations.id(partido.getClubVisitante()),
                partido.getGolesLocal(),
                partido.getGolesVisitante(),
                partido.getPenalesLocal(),
                partido.getPenalesVisitante(),
                relations.id(partido.getGanadorWalkover()),
                partido.getFechaCierre(),
                relations.id(partido.getCerradoPor()),
                participaciones.findByPartidoIdOrderByIdAsc(partido.getId()).stream()
                        .map(p -> new MinutosJugadorResponseDto(
                                p.getId(),
                                p.getJugador().getId(),
                                p.getClub().getId(),
                                p.getEsTitular(),
                                p.getMinutoEntrada(),
                                p.getMinutoSalida(),
                                p.getMinutosJugados()))
                        .toList());
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

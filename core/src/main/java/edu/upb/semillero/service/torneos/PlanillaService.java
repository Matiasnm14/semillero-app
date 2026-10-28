package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.CuerpoTecnicoRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.JugadorPlanillaRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.PlanillaRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.CuerpoTecnicoResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.EquipoPlanillaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.JugadorPlanillaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.PlanillaResponseDto;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.ParticipacionPartido;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.torneos.Cancha;
import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import edu.upb.semillero.repository.entity.torneos.ListaBuenaFe;
import edu.upb.semillero.repository.entity.torneos.Planilla;
import edu.upb.semillero.repository.entity.torneos.PlanillaCuerpoTecnico;
import edu.upb.semillero.repository.entity.torneos.Sancion;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.EstadoJugador;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.torneos.EstadoPlanilla;
import edu.upb.semillero.repository.enums.torneos.EstadoSancion;
import edu.upb.semillero.repository.repos.torneos.EventoPartidoRepository;
import edu.upb.semillero.repository.repos.torneos.InscripcionEquipoRepository;
import edu.upb.semillero.repository.repos.torneos.ListaBuenaFeRepository;
import edu.upb.semillero.repository.repos.torneos.ParticipacionPlanillaRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoFixtureRepository;
import edu.upb.semillero.repository.repos.torneos.PlanillaCuerpoTecnicoRepository;
import edu.upb.semillero.repository.repos.torneos.PlanillaRepository;
import edu.upb.semillero.repository.repos.torneos.SancionRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import edu.upb.semillero.service.fichatecnica.FichaTecnicaService;
import edu.upb.semillero.service.fichatecnica.JugadorParaTorneosDto;
import edu.upb.semillero.service.seguridad.AccesoTorneos;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 9 - Llenar la planilla del partido (tareas S3-01 a S3-04).
 *
 * <ul>
 *   <li><strong>S3-01</strong>: la planilla viene precargada con el torneo,
 *       la categoría, la fecha y hora, la cancha con su superficie, los equipos
 *       y el árbitro.</li>
 *   <li><strong>S3-02</strong>: titulares, suplentes y capitán salen de la
 *       lista de buena fe con su dorsal y se guardan en
 *       {@code participaciones_partido}. Los suspendidos y los no habilitados
 *       aparecen bloqueados y no se pueden poner.</li>
 *   <li><strong>S3-03</strong>: cuerpo técnico presente y observaciones del
 *       árbitro. Se guarda incompleta como BORRADOR y se retoma; confirmar
 *       exige que cada equipo tenga titulares y un capitán.</li>
 *   <li><strong>S3-04</strong>: el PDF lo arma {@code PdfTorneosService} con lo
 *       que devuelve {@link #precarga}.</li>
 * </ul>
 *
 * <p>La planilla la llena la mesa de control asignada al partido; el
 * organizador y el administrador pueden siempre (tarea S2-03).
 */
@Service
@Transactional(readOnly = true)
public class PlanillaService {

    private final PlanillaRepository repository;
    private final PlanillaCuerpoTecnicoRepository cuerpoTecnico;
    private final ParticipacionPlanillaRepository participaciones;
    private final PartidoFixtureRepository partidos;
    private final InscripcionEquipoRepository inscripciones;
    private final ListaBuenaFeRepository listas;
    private final SancionRepository sanciones;
    private final EventoPartidoRepository eventos;
    private final FichaTecnicaService fichaTecnica;
    private final AccesoTorneos acceso;
    private final EntityRelations relations;

    public PlanillaService(PlanillaRepository repository, PlanillaCuerpoTecnicoRepository cuerpoTecnico,
            ParticipacionPlanillaRepository participaciones, PartidoFixtureRepository partidos,
            InscripcionEquipoRepository inscripciones, ListaBuenaFeRepository listas,
            SancionRepository sanciones, EventoPartidoRepository eventos,
            FichaTecnicaService fichaTecnica, AccesoTorneos acceso, EntityRelations relations) {
        this.repository = repository;
        this.cuerpoTecnico = cuerpoTecnico;
        this.participaciones = participaciones;
        this.partidos = partidos;
        this.inscripciones = inscripciones;
        this.listas = listas;
        this.sanciones = sanciones;
        this.eventos = eventos;
        this.fichaTecnica = fichaTecnica;
        this.acceso = acceso;
        this.relations = relations;
    }

    /** Tarea S3-01: la planilla precargada, esté guardada o no. */
    public PlanillaResponseDto precarga(Long partidoId) {
        return toResponse(requirePartido(partidoId));
    }

    /**
     * Tareas S3-02 y S3-03: guarda la planilla. Si ya estaba confirmada, sigue
     * confirmada y vuelve a pasar por las reglas de completitud.
     */
    @Transactional
    public PlanillaResponseDto guardar(Long partidoId, PlanillaRequestDto request) {
        Partido partido = requirePartido(partidoId);
        requireEditable(partido);
        Planilla planilla = repository.findByPartidoId(partidoId).orElseGet(() -> nueva(partido));
        planilla.setObservacionesArbitro(request.observacionesArbitro());
        planilla.setUsuario(relations.find(Usuario.class, request.usuarioId()));
        Planilla guardada = repository.saveAndFlush(planilla);

        sincronizarJugadores(partido, request.jugadores());
        reemplazarCuerpoTecnico(guardada, partido,
                request.cuerpoTecnico() == null ? List.of() : request.cuerpoTecnico());
        if (guardada.getEstado() == EstadoPlanilla.CONFIRMADA) {
            validarCompleta(partido);
        }
        return toResponse(partido);
    }

    /** Tarea S3-03: confirma la planilla si cada equipo tiene titulares y un capitán. */
    @Transactional
    public PlanillaResponseDto confirmar(Long partidoId, Long usuarioId) {
        Partido partido = requirePartido(partidoId);
        requireEditable(partido);
        Planilla planilla = repository.findByPartidoId(partidoId)
                .orElseThrow(() -> new BusinessException("El partido " + partidoId
                        + " todavía no tiene planilla: guardala antes de confirmarla"));
        if (planilla.getEstado() == EstadoPlanilla.CONFIRMADA) {
            throw new ConflictException("La planilla del partido " + partidoId + " ya está confirmada");
        }
        validarCompleta(partido);
        planilla.setEstado(EstadoPlanilla.CONFIRMADA);
        planilla.setFechaConfirmacion(LocalDateTime.now());
        planilla.setUsuario(relations.find(Usuario.class, usuarioId));
        repository.saveAndFlush(planilla);
        return toResponse(partido);
    }

    private Partido requirePartido(Long partidoId) {
        Partido partido = partidos.findById(partidoId)
                .orElseThrow(() -> new NotFoundException("Partido con id " + partidoId + " no existe"));
        if (partido.getTorneoCategoria() == null) {
            throw new BusinessException("El partido " + partidoId
                    + " no es de un torneo: no tiene planilla");
        }
        return partido;
    }

    /** La planilla se toca hasta que el partido se cierra; la mesa, solo si es la asignada. */
    private void requireEditable(Partido partido) {
        acceso.requirePartidoAsignado(partido.getId(), relations.id(partido.getMesaControl()));
        if (partido.getEstado() == EstadoPartido.FINALIZADO
                || partido.getEstado() == EstadoPartido.CANCELADO) {
            throw new ConflictException("El partido " + partido.getId() + " está "
                    + partido.getEstado() + ": su planilla ya no se modifica");
        }
    }

    private Planilla nueva(Partido partido) {
        Planilla planilla = new Planilla();
        planilla.setPartido(partido);
        planilla.setEstado(EstadoPlanilla.BORRADOR);
        return planilla;
    }

    /**
     * Tarea S3-02: deja en {@code participaciones_partido} exactamente los
     * jugadores pedidos. Club y dorsal salen de la lista de buena fe.
     */
    private void sincronizarJugadores(Partido partido, List<JugadorPlanillaRequestDto> pedidos) {
        Set<Long> repetidos = new HashSet<>();
        for (JugadorPlanillaRequestDto pedido : pedidos) {
            if (!repetidos.add(pedido.jugadorId())) {
                throw new BusinessException("El jugador " + pedido.jugadorId()
                        + " está dos veces en la planilla");
            }
        }
        Map<Long, ParticipacionPartido> actuales = participaciones
                .findByPartidoIdOrderByIdAsc(partido.getId()).stream()
                .collect(Collectors.toMap(p -> p.getJugador().getId(), Function.identity()));

        for (ParticipacionPartido actual : actuales.values()) {
            if (!repetidos.contains(actual.getJugador().getId())) {
                if (eventos.existsByParticipacionIdOrParticipacionRelacionadaId(
                        actual.getId(), actual.getId())) {
                    throw new ConflictException("El jugador " + actual.getJugador().getId()
                            + " tiene eventos registrados en el partido: no se puede sacar de la"
                            + " planilla");
                }
                participaciones.delete(actual);
            }
        }
        participaciones.flush();

        for (JugadorPlanillaRequestDto pedido : pedidos) {
            ListaBuenaFe enLista = requireEnLista(partido, pedido.jugadorId());
            String bloqueo = motivoBloqueo(enLista, partido);
            if (bloqueo != null) {
                throw new BusinessException("El jugador " + pedido.jugadorId()
                        + " está bloqueado para este partido: " + bloqueo);
            }
            ParticipacionPartido participacion = actuales.get(pedido.jugadorId());
            if (participacion == null) {
                participacion = new ParticipacionPartido();
                participacion.setPartido(partido);
                participacion.setJugador(enLista.getJugador());
                participacion.setMinutoEntrada((short) 0);
            }
            participacion.setClub(enLista.getInscripcionEquipo().getClub());
            participacion.setDorsal(enLista.getDorsal());
            participacion.setEsTitular(pedido.esTitular());
            participacion.setCapitan(Boolean.TRUE.equals(pedido.capitan()));
            participaciones.save(participacion);
        }
        participaciones.flush();
        validarPorEquipo(partido, false);
    }

    /** El jugador tiene que estar en la lista de buena fe de uno de los dos equipos del partido. */
    private ListaBuenaFe requireEnLista(Partido partido, Long jugadorId) {
        Torneo torneo = partido.getTorneoCategoria().getTorneo();
        return listas.findByTorneoIdAndJugadorId(torneo.getId(), jugadorId)
                .filter(lista -> {
                    InscripcionEquipo inscripcion = lista.getInscripcionEquipo();
                    Long clubId = inscripcion.getClub().getId();
                    return Objects.equals(inscripcion.getTorneoCategoria().getId(),
                            partido.getTorneoCategoria().getId())
                            && (Objects.equals(clubId, partido.getClubLocal().getId())
                                    || Objects.equals(clubId, partido.getClubVisitante().getId()));
                })
                .orElseThrow(() -> new BusinessException("El jugador " + jugadorId
                        + " no está en la lista de buena fe de ninguno de los dos equipos del partido"));
    }

    /**
     * Tarea S3-02: por qué un jugador no puede jugar este partido, o
     * {@code null} si puede. Bloquean la inhabilitación de la lista de buena fe,
     * una sanción vigente en este torneo y un estado distinto de ACTIVO en ficha
     * técnica. Descontar las fechas de la sanción es la tarea S4-03.
     */
    private String motivoBloqueo(ListaBuenaFe enLista, Partido partido) {
        if (!Boolean.TRUE.equals(enLista.getHabilitado())) {
            return enLista.getMotivoInhabilitacion() == null
                    ? "no está habilitado en la lista de buena fe"
                    : enLista.getMotivoInhabilitacion();
        }
        List<Sancion> vigentes = sanciones.findByListaBuenaFeIdAndEstado(enLista.getId(),
                EstadoSancion.VIGENTE);
        if (!vigentes.isEmpty()) {
            Sancion sancion = vigentes.get(0);
            return "está suspendido, le quedan "
                    + (sancion.getFechasSuspension() - sancion.getFechasCumplidas())
                    + " fecha(s) por cumplir";
        }
        Optional<JugadorParaTorneosDto> datos = fichaTecnica.datosParaTorneos(
                enLista.getJugador().getId(), partido.getFechaInicio().toLocalDate());
        if (datos.isEmpty()) {
            return "no existe en ficha técnica";
        }
        if (datos.get().estado() != EstadoJugador.ACTIVO) {
            return "está " + datos.get().estado() + " en ficha técnica";
        }
        return null;
    }

    /** Tarea S3-03: reemplaza el cuerpo técnico presente. */
    private void reemplazarCuerpoTecnico(Planilla planilla, Partido partido,
            List<CuerpoTecnicoRequestDto> pedidos) {
        cuerpoTecnico.deleteAll(cuerpoTecnico.findByPlanillaId(planilla.getId()));
        cuerpoTecnico.flush();
        for (CuerpoTecnicoRequestDto pedido : pedidos) {
            if (!juegaElPartido(partido, pedido.clubId())) {
                throw new BusinessException("El club " + pedido.clubId()
                        + " no juega este partido: no puede tener cuerpo técnico en la planilla");
            }
            PlanillaCuerpoTecnico integrante = new PlanillaCuerpoTecnico();
            integrante.setPlanilla(planilla);
            integrante.setClub(relations.find(Club.class, pedido.clubId()));
            integrante.setNombreCompleto(pedido.nombreCompleto().trim());
            integrante.setCargo(pedido.cargo().trim());
            integrante.setUsuario(relations.find(Usuario.class, pedido.usuarioId()));
            cuerpoTecnico.save(integrante);
        }
        cuerpoTecnico.flush();
    }

    /**
     * Confirmar: cada equipo con inscripción tiene entre 1 titular y los
     * jugadores en cancha de la categoría, y exactamente un capitán. El rival
     * de un campeonato externo no tiene lista y no se controla. También se
     * revisan otra vez los bloqueos, por si apareció una sanción después de
     * guardar.
     */
    private void validarCompleta(Partido partido) {
        validarPorEquipo(partido, true);
        for (ParticipacionPartido participacion : participaciones
                .findByPartidoIdOrderByIdAsc(partido.getId())) {
            ListaBuenaFe enLista = requireEnLista(partido, participacion.getJugador().getId());
            String bloqueo = motivoBloqueo(enLista, partido);
            if (bloqueo != null) {
                throw new BusinessException("El jugador " + participacion.getJugador().getId()
                        + " está bloqueado para este partido: " + bloqueo);
            }
        }
    }

    /**
     * Al guardar: no más titulares que jugadores en cancha y como mucho un
     * capitán por equipo. Al confirmar, además: al menos un titular y un
     * capitán por cada equipo que tiene lista.
     */
    private void validarPorEquipo(Partido partido, boolean completa) {
        TorneoCategoria categoria = partido.getTorneoCategoria();
        List<ParticipacionPartido> cargados = participaciones.findByPartidoIdOrderByIdAsc(partido.getId());
        boolean algunEquipoConLista = false;
        for (Club club : List.of(partido.getClubLocal(), partido.getClubVisitante())) {
            List<ParticipacionPartido> delClub = cargados.stream()
                    .filter(p -> Objects.equals(p.getClub().getId(), club.getId()))
                    .toList();
            long titulares = delClub.stream().filter(p -> Boolean.TRUE.equals(p.getEsTitular())).count();
            long capitanes = delClub.stream().filter(p -> Boolean.TRUE.equals(p.getCapitan())).count();
            if (titulares > categoria.getJugadoresEnCancha()) {
                throw new BusinessException(club.getNombreClub() + " tiene " + titulares
                        + " titulares y en la categoría " + categoria.getNombre() + " juegan "
                        + categoria.getJugadoresEnCancha());
            }
            if (capitanes > 1) {
                throw new BusinessException(club.getNombreClub() + " tiene " + capitanes
                        + " capitanes: puede tener uno solo");
            }
            if (!completa || inscripcion(partido, club).isEmpty()) {
                continue;
            }
            algunEquipoConLista = true;
            if (titulares == 0) {
                throw new BusinessException(club.getNombreClub()
                        + " no tiene titulares en la planilla");
            }
            if (capitanes == 0) {
                throw new BusinessException(club.getNombreClub()
                        + " no tiene capitán en la planilla");
            }
        }
        if (completa && !algunEquipoConLista) {
            throw new BusinessException("Ninguno de los dos equipos del partido " + partido.getId()
                    + " está inscrito en la categoría: no hay lista de buena fe para la planilla");
        }
    }

    private boolean juegaElPartido(Partido partido, Long clubId) {
        return Objects.equals(clubId, partido.getClubLocal().getId())
                || Objects.equals(clubId, partido.getClubVisitante().getId());
    }

    private Optional<InscripcionEquipo> inscripcion(Partido partido, Club club) {
        return inscripciones.findByTorneoCategoriaIdAndClubId(partido.getTorneoCategoria().getId(),
                club.getId());
    }

    private PlanillaResponseDto toResponse(Partido partido) {
        TorneoCategoria categoria = partido.getTorneoCategoria();
        Torneo torneo = categoria.getTorneo();
        Cancha cancha = partido.getCancha();
        Optional<Planilla> planilla = repository.findByPartidoId(partido.getId());
        Map<Long, ParticipacionPartido> cargados = participaciones
                .findByPartidoIdOrderByIdAsc(partido.getId()).stream()
                .collect(Collectors.toMap(p -> p.getJugador().getId(), Function.identity()));
        List<PlanillaCuerpoTecnico> tecnicos = planilla
                .map(p -> cuerpoTecnico.findByPlanillaId(p.getId()))
                .orElse(List.of());
        return new PlanillaResponseDto(
                planilla.map(Planilla::getId).orElse(null),
                planilla.map(Planilla::getEstado).orElse(null),
                partido.getId(),
                partido.getEstado(),
                torneo.getId(),
                torneo.getNombre(),
                categoria.getId(),
                categoria.getNombre(),
                partido.getFase(),
                partido.getJornada(),
                partido.getFechaInicio(),
                relations.id(cancha),
                cancha == null ? null : cancha.getNombre(),
                cancha == null ? null : cancha.getTipoSuperficie(),
                cancha == null ? null : cancha.getDescripcion(),
                partido.getArbitro(),
                relations.id(partido.getMesaControl()),
                categoria.getJugadoresEnCancha(),
                planilla.map(Planilla::getObservacionesArbitro).orElse(null),
                planilla.map(p -> relations.id(p.getUsuario())).orElse(null),
                planilla.map(Planilla::getFechaConfirmacion).orElse(null),
                equipo(partido, partido.getClubLocal(), cargados, tecnicos),
                equipo(partido, partido.getClubVisitante(), cargados, tecnicos));
    }

    private EquipoPlanillaResponseDto equipo(Partido partido, Club club,
            Map<Long, ParticipacionPartido> cargados, List<PlanillaCuerpoTecnico> tecnicos) {
        Optional<InscripcionEquipo> inscripcion = inscripcion(partido, club);
        List<JugadorPlanillaResponseDto> jugadores = inscripcion
                .map(i -> listas.findByInscripcionEquipoIdOrderByDorsalAsc(i.getId()))
                .orElse(List.of())
                .stream()
                .map(enLista -> jugador(enLista, partido, cargados.get(enLista.getJugador().getId())))
                .toList();
        int titulares = (int) jugadores.stream()
                .filter(j -> j.enPlanilla() && Boolean.TRUE.equals(j.esTitular())).count();
        int suplentes = (int) jugadores.stream()
                .filter(j -> j.enPlanilla() && !Boolean.TRUE.equals(j.esTitular())).count();
        return new EquipoPlanillaResponseDto(
                club.getId(),
                club.getNombreClub(),
                inscripcion.map(InscripcionEquipo::getId).orElse(null),
                titulares,
                suplentes,
                jugadores,
                tecnicos.stream()
                        .filter(t -> Objects.equals(t.getClub().getId(), club.getId()))
                        .map(this::toResponse)
                        .toList());
    }

    private JugadorPlanillaResponseDto jugador(ListaBuenaFe enLista, Partido partido,
            ParticipacionPartido participacion) {
        String bloqueo = motivoBloqueo(enLista, partido);
        return new JugadorPlanillaResponseDto(
                enLista.getJugador().getId(),
                enLista.getId(),
                enLista.getDorsal(),
                relations.id(enLista.getPosicion()),
                bloqueo != null,
                bloqueo,
                participacion != null,
                participacion == null ? null : participacion.getId(),
                participacion == null ? null : participacion.getEsTitular(),
                participacion == null ? null : participacion.getCapitan(),
                participacion == null ? null : participacion.getMinutoEntrada(),
                participacion == null ? null : participacion.getMinutoSalida(),
                participacion == null ? null : participacion.getMinutosJugados());
    }

    private CuerpoTecnicoResponseDto toResponse(PlanillaCuerpoTecnico entity) {
        return new CuerpoTecnicoResponseDto(
                entity.getId(),
                relations.id(entity.getClub()),
                entity.getNombreCompleto(),
                entity.getCargo(),
                relations.id(entity.getUsuario()));
    }
}

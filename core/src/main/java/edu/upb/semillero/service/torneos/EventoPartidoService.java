package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.EventoPartidoRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.EventoPartidoResponseDto;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.ParticipacionPartido;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.torneos.EventoPartido;
import edu.upb.semillero.repository.entity.torneos.Planilla;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.torneos.EstadoPlanilla;
import edu.upb.semillero.repository.enums.torneos.TipoEventoPartido;
import edu.upb.semillero.repository.enums.torneos.TipoGol;
import edu.upb.semillero.repository.repos.torneos.EventoPartidoRepository;
import edu.upb.semillero.repository.repos.torneos.ParticipacionPlanillaRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoFixtureRepository;
import edu.upb.semillero.repository.repos.torneos.PlanillaRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import edu.upb.semillero.service.seguridad.AccesoTorneos;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 10 - Registrar los eventos del partido (tareas S3-06 y S3-07).
 *
 * <ul>
 *   <li><strong>S3-06</strong>: goles con autor, minuto, tipo (jugada, penal,
 *       tiro libre o autogol) y asistencia opcional.</li>
 *   <li><strong>S3-07</strong>: tarjetas con motivo, y cambios con minuto,
 *       el que sale y el que entra.</li>
 * </ul>
 *
 * <p>Solo jugadores de la planilla, y la planilla tiene que estar confirmada.
 * El que hace un gol, da una asistencia o sale en un cambio tiene que estar en
 * cancha en ese minuto; el que entra tiene que ser un suplente que todavía no
 * jugó. Un expulsado no vuelve a aparecer. El primer evento pone el partido EN_CURSO.
 *
 * <p>Los eventos no tocan {@code participaciones_partido}: los minutos de
 * cada jugador se calculan al cerrar el partido (tarea S3-08), así un evento
 * cargado mal se borra sin dejar nada a medias.
 */
@Service
@Transactional(readOnly = true)
public class EventoPartidoService {
    /** Una amarilla más que esto ya es expulsión: corresponde cargar la roja. */
    private static final int MAX_AMARILLAS = 2;

    private final EventoPartidoRepository repository;
    private final PartidoFixtureRepository partidos;
    private final PlanillaRepository planillas;
    private final ParticipacionPlanillaRepository participaciones;
    private final AccesoTorneos acceso;
    private final EntityRelations relations;

    public EventoPartidoService(EventoPartidoRepository repository, PartidoFixtureRepository partidos,
            PlanillaRepository planillas, ParticipacionPlanillaRepository participaciones,
            AccesoTorneos acceso, EntityRelations relations) {
        this.repository = repository;
        this.partidos = partidos;
        this.planillas = planillas;
        this.participaciones = participaciones;
        this.acceso = acceso;
        this.relations = relations;
    }

    public List<EventoPartidoResponseDto> findByPartido(Long partidoId) {
        requirePartido(partidoId);
        return repository.findByPartidoIdOrderByMinutoAscIdAsc(partidoId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EventoPartidoResponseDto registrar(Long partidoId, EventoPartidoRequestDto request) {
        Partido partido = requirePartido(partidoId);
        requireAbierto(partido);
        requirePlanillaConfirmada(partido);

        List<ParticipacionPartido> planilla = participaciones.findByPartidoIdOrderByIdAsc(partidoId);
        List<EventoPartido> anteriores = repository.findByPartidoIdOrderByMinutoAscIdAsc(partidoId);
        Map<Long, MinutosJugados.Tramo> tramos = MinutosJugados.tramos(planilla, anteriores);
        ParticipacionPartido protagonista = requireEnPlanilla(partidoId, request.jugadorId());
        ParticipacionPartido relacionado = request.jugadorRelacionadoId() == null
                ? null : requireEnPlanilla(partidoId, request.jugadorRelacionadoId());
        if (relacionado != null && Objects.equals(relacionado.getId(), protagonista.getId())) {
            throw new BusinessException("El jugador " + request.jugadorId()
                    + " no puede ser a la vez los dos jugadores del evento");
        }
        requireNoExpulsado(protagonista, tramos);

        EventoPartido evento = new EventoPartido();
        evento.setPartido(partido);
        evento.setTipo(request.tipo());
        evento.setMinuto(request.minuto());
        evento.setParticipacion(protagonista);
        evento.setParticipacionRelacionada(relacionado);
        switch (request.tipo()) {
            case GOL -> validarGol(evento, request, partido, tramos);
            case AMARILLA, ROJA -> validarTarjeta(evento, request, anteriores);
            case CAMBIO -> validarCambio(evento, request, partido, tramos, anteriores);
        }
        evento.setUsuario(relations.find(Usuario.class, request.usuarioId()));
        evento.setFechaRegistro(LocalDateTime.now());
        EventoPartido guardado = repository.saveAndFlush(evento);

        if (partido.getEstado() == EstadoPartido.PROGRAMADO) {
            partido.setEstado(EstadoPartido.EN_CURSO);
            partidos.saveAndFlush(partido);
        }
        return toResponse(guardado);
    }

    /**
     * Corregir un evento mal cargado. Un cambio no se borra si el que entró ya
     * tiene otros eventos, porque quedarían sin sentido.
     */
    @Transactional
    public void delete(Long partidoId, Long id) {
        Partido partido = requirePartido(partidoId);
        requireAbierto(partido);
        EventoPartido evento = repository.findById(id)
                .filter(e -> Objects.equals(e.getPartido().getId(), partidoId))
                .orElseThrow(() -> new NotFoundException("El evento con id " + id
                        + " no existe en el partido " + partidoId));
        if (evento.getTipo() == TipoEventoPartido.CAMBIO) {
            Long entra = evento.getParticipacionRelacionada().getId();
            boolean tieneOtros = repository.findByPartidoIdOrderByMinutoAscIdAsc(partidoId).stream()
                    .filter(otro -> !Objects.equals(otro.getId(), evento.getId()))
                    .anyMatch(otro -> Objects.equals(otro.getParticipacion().getId(), entra)
                            || otro.getParticipacionRelacionada() != null
                                    && Objects.equals(otro.getParticipacionRelacionada().getId(), entra));
            if (tieneOtros) {
                throw new ConflictException("El jugador que entró en el cambio " + id
                        + " tiene eventos después: borralos antes de borrar el cambio");
            }
        }
        repository.delete(evento);
        repository.flush();
    }

    private Partido requirePartido(Long partidoId) {
        Partido partido = partidos.findById(partidoId)
                .orElseThrow(() -> new NotFoundException("Partido con id " + partidoId + " no existe"));
        if (partido.getTorneoCategoria() == null) {
            throw new BusinessException("El partido " + partidoId
                    + " no es de un torneo: no lleva eventos");
        }
        return partido;
    }

    /** Un partido cerrado solo se toca reabriéndolo (tarea S3-10); uno anulado, nunca. */
    private void requireAbierto(Partido partido) {
        acceso.requirePartidoAsignado(partido.getId(), relations.id(partido.getMesaControl()));
        if (partido.getEstado() == EstadoPartido.FINALIZADO) {
            throw new ConflictException("El partido " + partido.getId()
                    + " está cerrado: para corregirlo, el organizador tiene que reabrirlo");
        }
        if (partido.getEstado() == EstadoPartido.CANCELADO) {
            throw new ConflictException("El partido " + partido.getId()
                    + " está anulado (CANCELADO): no lleva eventos");
        }
    }

    private void requirePlanillaConfirmada(Partido partido) {
        Planilla planilla = planillas.findByPartidoId(partido.getId())
                .orElseThrow(() -> new BusinessException("El partido " + partido.getId()
                        + " no tiene planilla: los eventos son de jugadores de la planilla"));
        if (planilla.getEstado() != EstadoPlanilla.CONFIRMADA) {
            throw new BusinessException("La planilla del partido " + partido.getId()
                    + " no está confirmada: confirmala antes de cargar eventos");
        }
    }

    private ParticipacionPartido requireEnPlanilla(Long partidoId, Long jugadorId) {
        return participaciones.findByPartidoIdAndJugadorId(partidoId, jugadorId)
                .orElseThrow(() -> new BusinessException("El jugador " + jugadorId
                        + " no está en la planilla del partido " + partidoId));
    }

    private void requireNoExpulsado(ParticipacionPartido participacion,
            Map<Long, MinutosJugados.Tramo> tramos) {
        MinutosJugados.Tramo tramo = tramos.get(participacion.getId());
        if (tramo != null && tramo.expulsado()) {
            throw new BusinessException("El jugador " + participacion.getJugador().getId()
                    + " ya fue expulsado: no puede tener más eventos en el partido");
        }
    }

    /** Tarea S3-06: el autor y la asistencia en cancha; en un autogol el gol suma al rival. */
    private void validarGol(EventoPartido evento, EventoPartidoRequestDto request, Partido partido,
            Map<Long, MinutosJugados.Tramo> tramos) {
        if (request.tipoGol() == null) {
            throw new BusinessException("Un gol lleva su tipo: JUGADA, PENAL, TIRO_LIBRE o AUTOGOL");
        }
        ParticipacionPartido autor = evento.getParticipacion();
        requireEnCancha(autor, tramos, request.minuto(), "hacer el gol");
        ParticipacionPartido asistencia = evento.getParticipacionRelacionada();
        if (asistencia != null) {
            if (request.tipoGol() == TipoGol.AUTOGOL) {
                throw new BusinessException("Un autogol no lleva asistencia");
            }
            if (!Objects.equals(asistencia.getClub().getId(), autor.getClub().getId())) {
                throw new BusinessException("La asistencia tiene que ser de un compañero del autor");
            }
            requireEnCancha(asistencia, tramos, request.minuto(), "dar la asistencia");
        }
        evento.setTipoGol(request.tipoGol());
        evento.setMotivo(null);
        evento.setClub(request.tipoGol() == TipoGol.AUTOGOL
                ? rivalDe(partido, autor.getClub())
                : autor.getClub());
    }

    /** Tarea S3-07: tarjetas con motivo; como mucho dos amarillas, la siguiente es roja. */
    private void validarTarjeta(EventoPartido evento, EventoPartidoRequestDto request,
            List<EventoPartido> anteriores) {
        if (request.tipoGol() != null) {
            throw new BusinessException("Solo los goles llevan tipo de gol");
        }
        if (evento.getParticipacionRelacionada() != null) {
            throw new BusinessException("Una tarjeta es de un solo jugador");
        }
        if (request.motivo() == null || request.motivo().isBlank()) {
            throw new BusinessException("La tarjeta lleva el motivo");
        }
        Long amonestado = evento.getParticipacion().getId();
        if (request.tipo() == TipoEventoPartido.AMARILLA) {
            long amarillas = anteriores.stream()
                    .filter(e -> e.getTipo() == TipoEventoPartido.AMARILLA)
                    .filter(e -> Objects.equals(e.getParticipacion().getId(), amonestado))
                    .count();
            if (amarillas >= MAX_AMARILLAS) {
                throw new BusinessException("El jugador " + evento.getParticipacion().getJugador().getId()
                        + " ya tiene " + amarillas + " amarillas: corresponde cargar la roja");
            }
        }
        evento.setTipoGol(null);
        evento.setMotivo(request.motivo().trim());
        evento.setClub(evento.getParticipacion().getClub());
    }

    /**
     * Tarea S3-07: sale uno que está en cancha y entra un suplente del mismo
     * equipo que todavía no jugó, sin pasar el máximo de cambios de la
     * categoría.
     */
    private void validarCambio(EventoPartido evento, EventoPartidoRequestDto request, Partido partido,
            Map<Long, MinutosJugados.Tramo> tramos, List<EventoPartido> anteriores) {
        if (request.tipoGol() != null) {
            throw new BusinessException("Solo los goles llevan tipo de gol");
        }
        ParticipacionPartido sale = evento.getParticipacion();
        ParticipacionPartido entra = evento.getParticipacionRelacionada();
        if (entra == null) {
            throw new BusinessException("El cambio lleva el jugador que entra en jugadorRelacionadoId");
        }
        if (!Objects.equals(sale.getClub().getId(), entra.getClub().getId())) {
            throw new BusinessException("El que sale y el que entra tienen que ser del mismo equipo");
        }
        requireEnCancha(sale, tramos, request.minuto(), "salir en el cambio");
        MinutosJugados.Tramo tramoEntra = tramos.get(entra.getId());
        if (tramoEntra != null && tramoEntra.expulsado()) {
            throw new BusinessException("El jugador " + entra.getJugador().getId()
                    + " fue expulsado: no puede entrar");
        }
        if (tramoEntra == null || tramoEntra.entro()) {
            throw new BusinessException("El jugador " + entra.getJugador().getId()
                    + " ya jugó en el partido: solo entra un suplente que todavía no jugó");
        }
        Short maximo = partido.getTorneoCategoria().getMaxSustituciones();
        if (maximo != null) {
            long hechos = anteriores.stream()
                    .filter(e -> e.getTipo() == TipoEventoPartido.CAMBIO)
                    .filter(e -> Objects.equals(e.getClub().getId(), sale.getClub().getId()))
                    .count();
            if (hechos >= maximo) {
                throw new BusinessException(sale.getClub().getNombreClub() + " ya hizo los "
                        + maximo + " cambios que permite la categoría");
            }
        }
        evento.setTipoGol(null);
        evento.setMotivo(request.motivo() == null || request.motivo().isBlank()
                ? null : request.motivo().trim());
        evento.setClub(sale.getClub());
    }

    private void requireEnCancha(ParticipacionPartido participacion,
            Map<Long, MinutosJugados.Tramo> tramos, int minuto, String paraQue) {
        MinutosJugados.Tramo tramo = tramos.get(participacion.getId());
        if (tramo == null || !tramo.enCanchaAl(minuto)) {
            throw new BusinessException("El jugador " + participacion.getJugador().getId()
                    + " no está en cancha en el minuto " + minuto + " para " + paraQue);
        }
    }

    private Club rivalDe(Partido partido, Club club) {
        return Objects.equals(partido.getClubLocal().getId(), club.getId())
                ? partido.getClubVisitante()
                : partido.getClubLocal();
    }

    private EventoPartidoResponseDto toResponse(EventoPartido entity) {
        return new EventoPartidoResponseDto(
                entity.getId(),
                relations.id(entity.getPartido()),
                entity.getTipo(),
                entity.getMinuto(),
                entity.getParticipacion().getJugador().getId(),
                entity.getParticipacionRelacionada() == null
                        ? null : entity.getParticipacionRelacionada().getJugador().getId(),
                relations.id(entity.getClub()),
                entity.getTipoGol(),
                entity.getMotivo(),
                relations.id(entity.getUsuario()),
                entity.getFechaRegistro());
    }
}

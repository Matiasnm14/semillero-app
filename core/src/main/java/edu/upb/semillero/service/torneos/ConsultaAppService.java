package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.response.torneos.MiCuentaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.NotificacionResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.PartidoCerradoResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.PartidoFixtureResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.SancionResponseDto;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.Notificacion;
import edu.upb.semillero.repository.entity.torneos.ListaBuenaFe;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.torneos.EstadoTorneo;
import edu.upb.semillero.repository.repos.NotificacionRepository;
import edu.upb.semillero.repository.repos.torneos.JugadorConsultaRepository;
import edu.upb.semillero.repository.repos.torneos.ListaBuenaFeRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoFixtureRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.comun.ComunService;
import edu.upb.semillero.service.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 14 - Lo que consulta la app móvil (tareas S4-14, S4-15 y S4-16), con la
 * misma API REST y la misma cuenta que la web.
 *
 * <ul>
 *   <li>Próximos partidos con fecha, hora, cancha y superficie, de un jugador
 *       o de una categoría.</li>
 *   <li>"Mi cuenta": los jugadores de la cuenta, si alguno está suspendido,
 *       sus próximos partidos y los avisos sin leer (S4-15).</li>
 *   <li>Las notificaciones: reprogramación, resultado publicado y suspensión
 *       (S4-16).</li>
 * </ul>
 *
 * <p>La tabla y los goleadores de la categoría ya tienen su endpoint (HU 11 y
 * HU 12). Acá también está la lectura por {@code fecha_cierre} que usa el
 * microservicio de video si no le llegó {@code PartidoFinalizado} (HU 15).
 */
@Service
@Transactional(readOnly = true)
public class ConsultaAppService {
    private static final int PROXIMOS_EN_MI_CUENTA = 5;
    private static final Set<EstadoTorneo> CON_PARTIDOS =
            Set.of(EstadoTorneo.INSCRIPCIONES_ABIERTAS, EstadoTorneo.EN_CURSO);

    private final FixtureService fixture;
    private final SancionService sanciones;
    private final ListaBuenaFeRepository listas;
    private final TorneoCategoriaRepository categorias;
    private final PartidoFixtureRepository partidos;
    private final JugadorConsultaRepository jugadores;
    private final NotificacionRepository notificaciones;
    private final ComunService comun;
    private final EntityRelations relations;

    public ConsultaAppService(FixtureService fixture, SancionService sanciones,
            ListaBuenaFeRepository listas, TorneoCategoriaRepository categorias,
            PartidoFixtureRepository partidos, JugadorConsultaRepository jugadores,
            NotificacionRepository notificaciones, ComunService comun, EntityRelations relations) {
        this.fixture = fixture;
        this.sanciones = sanciones;
        this.listas = listas;
        this.categorias = categorias;
        this.partidos = partidos;
        this.jugadores = jugadores;
        this.notificaciones = notificaciones;
        this.comun = comun;
        this.relations = relations;
    }

    /** Tarea S4-14: los próximos partidos de los equipos donde está el jugador. */
    public List<PartidoFixtureResponseDto> proximosDeJugador(Long jugadorId) {
        LocalDateTime ahora = LocalDateTime.now();
        List<PartidoFixtureResponseDto> proximos = new ArrayList<>();
        for (ListaBuenaFe lista : listas.findByJugadorIdOrderByIdDesc(jugadorId)) {
            if (!CON_PARTIDOS.contains(lista.getTorneo().getEstado())) {
                continue;
            }
            proximos.addAll(fixture.proximos(lista.getInscripcionEquipo().getTorneoCategoria().getId(),
                    lista.getInscripcionEquipo().getClub().getId(), ahora));
        }
        return proximos.stream()
                .sorted(Comparator.comparing(PartidoFixtureResponseDto::fechaInicio))
                .toList();
    }

    /** Tarea S4-14: los próximos partidos de una categoría. */
    public List<PartidoFixtureResponseDto> proximosDeCategoria(Long torneoId, Long categoriaId) {
        categorias.findByIdAndTorneoId(categoriaId, torneoId)
                .orElseThrow(() -> new NotFoundException("La categoría con id " + categoriaId
                        + " no existe en el torneo " + torneoId));
        return fixture.proximos(categoriaId, null, LocalDateTime.now());
    }

    /** Tarea S4-15: lo que la app muestra al entrar, con el aviso de suspensión vigente. */
    public MiCuentaResponseDto miCuenta(Long usuarioId) {
        List<Long> jugadorIds = jugadores.findByUsuarioIdOrderByIdAsc(usuarioId).stream()
                .map(Jugador::getId)
                .toList();
        List<SancionResponseDto> vigentes = new ArrayList<>();
        List<PartidoFixtureResponseDto> proximos = new ArrayList<>();
        for (Long jugadorId : jugadorIds) {
            vigentes.addAll(sanciones.delJugador(jugadorId, true));
            proximos.addAll(proximosDeJugador(jugadorId));
        }
        return new MiCuentaResponseDto(
                usuarioId,
                comun.rolesDe(usuarioId),
                jugadorIds,
                !vigentes.isEmpty(),
                vigentes,
                proximos.stream()
                        .sorted(Comparator.comparing(PartidoFixtureResponseDto::fechaInicio))
                        .distinct()
                        .limit(PROXIMOS_EN_MI_CUENTA)
                        .toList(),
                notificaciones.countByUsuarioIdAndLeidaFalse(usuarioId));
    }

    /** Tarea S4-16: los avisos del usuario, del más nuevo al más viejo. */
    public List<NotificacionResponseDto> notificaciones(Long usuarioId, boolean soloNoLeidas) {
        return (soloNoLeidas
                ? notificaciones.findByUsuarioIdAndLeidaFalseOrderByFechaDescIdDesc(usuarioId)
                : notificaciones.findByUsuarioIdOrderByFechaDescIdDesc(usuarioId)).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Tarea S4-16: marcar un aviso como leído; solo uno propio. */
    @Transactional
    public NotificacionResponseDto marcarLeida(Long id, Long usuarioId) {
        Notificacion aviso = notificaciones.findById(id)
                .filter(n -> Objects.equals(relations.id(n.getUsuario()), usuarioId))
                .orElseThrow(() -> new NotFoundException("La notificación con id " + id
                        + " no existe entre las tuyas"));
        aviso.setLeida(true);
        return toResponse(notificaciones.saveAndFlush(aviso));
    }

    /** Tarea S4-16: marcar todos los avisos como leídos; devuelve cuántos cambió. */
    @Transactional
    public int marcarTodasLeidas(Long usuarioId) {
        List<Notificacion> sinLeer = notificaciones.findByUsuarioIdAndLeidaFalseOrderByFechaDescIdDesc(usuarioId);
        sinLeer.forEach(aviso -> aviso.setLeida(true));
        notificaciones.saveAllAndFlush(sinLeer);
        return sinLeer.size();
    }

    /**
     * HU 15: los partidos cerrados después de {@code desde}, para el
     * microservicio de video (punto 13 del acuerdo).
     */
    public List<PartidoCerradoResponseDto> cerradosDesde(LocalDateTime desde) {
        return partidos.findByEstadoAndFechaCierreAfterOrderByFechaCierreAsc(EstadoPartido.FINALIZADO,
                        desde == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : desde).stream()
                .filter(partido -> partido.getTorneoCategoria() != null)
                .map(partido -> new PartidoCerradoResponseDto(partido.getId(),
                        partido.getTorneoCategoria().getId(), partido.getFechaCierre()))
                .toList();
    }

    private NotificacionResponseDto toResponse(Notificacion entity) {
        return new NotificacionResponseDto(entity.getId(), entity.getModulo(), entity.getTitulo(),
                entity.getMensaje(), Boolean.TRUE.equals(entity.getLeida()), entity.getFecha());
    }
}

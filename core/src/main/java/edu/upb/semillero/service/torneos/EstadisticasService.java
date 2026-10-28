package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.response.torneos.EstadisticasCategoriaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.EstadisticasJugadorResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.FairPlayResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.RankingJugadorResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.VallaResponseDto;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.ParticipacionPartido;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.torneos.EventoPartido;
import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.torneos.EstadoInscripcion;
import edu.upb.semillero.repository.enums.torneos.TipoEventoPartido;
import edu.upb.semillero.repository.enums.torneos.TipoGol;
import edu.upb.semillero.repository.repos.torneos.EventoPartidoRepository;
import edu.upb.semillero.repository.repos.torneos.InscripcionEquipoRepository;
import edu.upb.semillero.repository.repos.torneos.ListaBuenaFeRepository;
import edu.upb.semillero.repository.repos.torneos.ParticipacionPlanillaRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoFixtureRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 12 - Goleadores y estadísticas (tareas S4-11 y S4-12).
 *
 * <ul>
 *   <li><strong>S4-11</strong>: por categoría, las tablas de goleadores,
 *       asistencias, vallas menos vencidas y fair play.</li>
 *   <li><strong>S4-12</strong>: el resumen de un jugador en el torneo:
 *       partidos, minutos, goles, asistencias y tarjetas.</li>
 * </ul>
 *
 * <p>Nada se guarda: todo sale de los partidos FINALIZADO, sus eventos y los
 * minutos que el cierre dejó en {@code participaciones_partido}, así que se
 * actualiza solo cada vez que se cierra un partido y un partido anulado
 * (CANCELADO) nunca cuenta. Los autogoles no suman al goleador. El W.O. no se
 * jugó: no suma partidos ni goles en contra a las vallas.
 */
@Service
@Transactional(readOnly = true)
public class EstadisticasService {
    private static final int PUNTOS_AMARILLA = 1;
    private static final int PUNTOS_ROJA = 3;

    private final TorneoRepository torneos;
    private final TorneoCategoriaRepository categorias;
    private final PartidoFixtureRepository partidos;
    private final EventoPartidoRepository eventos;
    private final ParticipacionPlanillaRepository participaciones;
    private final InscripcionEquipoRepository inscripciones;
    private final ListaBuenaFeRepository listas;

    public EstadisticasService(TorneoRepository torneos, TorneoCategoriaRepository categorias,
            PartidoFixtureRepository partidos, EventoPartidoRepository eventos,
            ParticipacionPlanillaRepository participaciones, InscripcionEquipoRepository inscripciones,
            ListaBuenaFeRepository listas) {
        this.torneos = torneos;
        this.categorias = categorias;
        this.partidos = partidos;
        this.eventos = eventos;
        this.participaciones = participaciones;
        this.inscripciones = inscripciones;
        this.listas = listas;
    }

    /** Tarea S4-11: las cuatro tablas de la categoría; {@code limite} corta las de jugadores. */
    public EstadisticasCategoriaResponseDto deCategoria(Long torneoId, Long categoriaId, int limite) {
        TorneoCategoria categoria = categorias.findByIdAndTorneoId(categoriaId, torneoId)
                .orElseThrow(() -> new NotFoundException("La categoría con id " + categoriaId
                        + " no existe en el torneo " + torneoId));
        List<Partido> cerrados = partidos.findByTorneoCategoriaIdOrderByJornadaAscFechaInicioAsc(
                        categoria.getId()).stream()
                .filter(partido -> partido.getEstado() == EstadoPartido.FINALIZADO)
                .toList();
        List<EventoPartido> delaCategoria = eventos.findByPartidoTorneoCategoriaIdAndPartidoEstado(
                categoria.getId(), EstadoPartido.FINALIZADO);
        Map<Long, Integer> jugados = partidosPorJugador(participaciones
                .findByPartidoTorneoCategoriaIdAndPartidoEstado(categoria.getId(), EstadoPartido.FINALIZADO));

        List<RankingJugadorResponseDto> goleadores = ranking(delaCategoria,
                evento -> evento.getTipo() == TipoEventoPartido.GOL && evento.getTipoGol() != TipoGol.AUTOGOL,
                EventoPartido::getParticipacion, jugados, limite);
        List<RankingJugadorResponseDto> asistencias = ranking(delaCategoria,
                evento -> evento.getTipo() == TipoEventoPartido.GOL
                        && evento.getParticipacionRelacionada() != null,
                EventoPartido::getParticipacionRelacionada, jugados, limite);

        Map<Long, Equipo> equipos = new LinkedHashMap<>();
        inscripciones.findByTorneoCategoriaIdAndEstadoOrderByIdAsc(categoria.getId(), EstadoInscripcion.APROBADA)
                .stream().map(InscripcionEquipo::getClub)
                .forEach(club -> equipos.put(club.getId(), new Equipo(club)));
        for (Partido partido : cerrados) {
            if (partido.getGanadorWalkover() != null) {
                continue;
            }
            Equipo local = equipos.computeIfAbsent(partido.getClubLocal().getId(),
                    id -> new Equipo(partido.getClubLocal()));
            Equipo visitante = equipos.computeIfAbsent(partido.getClubVisitante().getId(),
                    id -> new Equipo(partido.getClubVisitante()));
            local.jugo(goles(partido.getGolesVisitante()));
            visitante.jugo(goles(partido.getGolesLocal()));
        }
        for (EventoPartido evento : delaCategoria) {
            Equipo equipo = equipos.get(evento.getClub().getId());
            if (equipo == null) {
                continue;
            }
            if (evento.getTipo() == TipoEventoPartido.AMARILLA) {
                equipo.amarillas++;
            } else if (evento.getTipo() == TipoEventoPartido.ROJA) {
                equipo.rojas++;
            }
        }
        return new EstadisticasCategoriaResponseDto(categoria.getId(), cerrados.size(), goleadores,
                asistencias, vallas(equipos.values()), fairPlay(equipos.values()));
    }

    /** Tarea S4-12: el resumen del jugador en el torneo. */
    public EstadisticasJugadorResponseDto deJugador(Long torneoId, Long jugadorId) {
        if (!torneos.existsById(torneoId)) {
            throw new NotFoundException("Torneo con id " + torneoId + " no existe");
        }
        return buscarDeJugador(torneoId, jugadorId)
                .orElseThrow(() -> new NotFoundException("El jugador " + jugadorId
                        + " no está en ninguna lista de buena fe del torneo " + torneoId));
    }

    /**
     * Lo mismo, vacío si el jugador no está en el torneo. Lo usa
     * {@code TorneosConsultaService}, que no le tira excepciones a otro módulo:
     * una excepción dentro de esta transacción la dejaría marcada para rollback
     * en la de quien llama.
     */
    public Optional<EstadisticasJugadorResponseDto> buscarDeJugador(Long torneoId, Long jugadorId) {
        if (torneoId == null || jugadorId == null
                || listas.findByTorneoIdAndJugadorId(torneoId, jugadorId).isEmpty()) {
            return Optional.empty();
        }
        List<ParticipacionPartido> jugadas = participaciones
                .findByJugadorIdAndPartidoTorneoCategoriaTorneoIdAndPartidoEstado(jugadorId, torneoId,
                        EstadoPartido.FINALIZADO).stream()
                .filter(this::jugo)
                .toList();
        List<EventoPartido> propios = eventos
                .findByParticipacionJugadorIdAndPartidoTorneoCategoriaTorneoIdAndPartidoEstado(jugadorId,
                        torneoId, EstadoPartido.FINALIZADO);
        int asistencias = (int) eventos
                .findByParticipacionRelacionadaJugadorIdAndPartidoTorneoCategoriaTorneoIdAndPartidoEstado(
                        jugadorId, torneoId, EstadoPartido.FINALIZADO).stream()
                .filter(evento -> evento.getTipo() == TipoEventoPartido.GOL)
                .count();
        return Optional.of(new EstadisticasJugadorResponseDto(
                jugadorId,
                torneoId,
                jugadas.size(),
                (int) jugadas.stream().filter(p -> Boolean.TRUE.equals(p.getEsTitular())).count(),
                jugadas.stream().mapToInt(p -> p.getMinutosJugados() == null ? 0 : p.getMinutosJugados()).sum(),
                contar(propios, e -> e.getTipo() == TipoEventoPartido.GOL && e.getTipoGol() != TipoGol.AUTOGOL),
                asistencias,
                contar(propios, e -> e.getTipo() == TipoEventoPartido.GOL && e.getTipoGol() == TipoGol.AUTOGOL),
                contar(propios, e -> e.getTipo() == TipoEventoPartido.AMARILLA),
                contar(propios, e -> e.getTipo() == TipoEventoPartido.ROJA)));
    }

    /**
     * Jugó el partido si fue titular o si entró: el suplente que no entró queda
     * con salida 0 al cerrar. Un W.O. no se jugó.
     */
    private boolean jugo(ParticipacionPartido participacion) {
        if (participacion.getPartido().getGanadorWalkover() != null) {
            return false;
        }
        return Boolean.TRUE.equals(participacion.getEsTitular())
                || participacion.getMinutoSalida() != null && participacion.getMinutoSalida() > 0;
    }

    private Map<Long, Integer> partidosPorJugador(List<ParticipacionPartido> cerradas) {
        Map<Long, Integer> jugados = new LinkedHashMap<>();
        cerradas.stream().filter(this::jugo)
                .forEach(p -> jugados.merge(p.getJugador().getId(), 1, Integer::sum));
        return jugados;
    }

    /**
     * Goleadores o asistencias: cuenta los eventos que pasan el filtro por el
     * jugador que devuelve {@code quien}. A igual cantidad comparten el puesto,
     * y entre ellos va primero el que jugó menos partidos.
     */
    private List<RankingJugadorResponseDto> ranking(List<EventoPartido> delaCategoria,
            Predicate<EventoPartido> cuenta,
            Function<EventoPartido, ParticipacionPartido> quien,
            Map<Long, Integer> jugados, int limite) {
        Map<Long, int[]> cantidades = new LinkedHashMap<>();
        Map<Long, Club> clubes = new LinkedHashMap<>();
        for (EventoPartido evento : delaCategoria) {
            if (!cuenta.test(evento)) {
                continue;
            }
            ParticipacionPartido participacion = quien.apply(evento);
            Long jugadorId = participacion.getJugador().getId();
            cantidades.computeIfAbsent(jugadorId, id -> new int[1])[0]++;
            clubes.putIfAbsent(jugadorId, participacion.getClub());
        }
        List<Long> orden = cantidades.keySet().stream()
                .sorted(Comparator.comparingInt((Long id) -> cantidades.get(id)[0]).reversed()
                        .thenComparingInt(id -> jugados.getOrDefault(id, 0))
                        .thenComparing(Comparator.naturalOrder()))
                .limit(Math.max(limite, 0))
                .toList();
        List<RankingJugadorResponseDto> filas = new ArrayList<>();
        int posicion = 0;
        int anterior = -1;
        for (int i = 0; i < orden.size(); i++) {
            Long jugadorId = orden.get(i);
            int cantidad = cantidades.get(jugadorId)[0];
            if (cantidad != anterior) {
                posicion = i + 1;
                anterior = cantidad;
            }
            Club club = clubes.get(jugadorId);
            filas.add(new RankingJugadorResponseDto(posicion, jugadorId, club.getId(), club.getNombreClub(),
                    cantidad, jugados.getOrDefault(jugadorId, 0)));
        }
        return filas;
    }

    /** Menos goles en contra por partido; a igual promedio, el que jugó más. */
    private List<VallaResponseDto> vallas(Collection<Equipo> equipos) {
        List<Equipo> conPartidos = equipos.stream()
                .filter(equipo -> equipo.jugados > 0)
                .sorted(Comparator.comparingDouble(Equipo::promedio)
                        .thenComparing(Comparator.comparingInt((Equipo e) -> e.jugados).reversed())
                        .thenComparing(e -> e.club.getId()))
                .toList();
        List<VallaResponseDto> filas = new ArrayList<>();
        for (int i = 0; i < conPartidos.size(); i++) {
            Equipo equipo = conPartidos.get(i);
            filas.add(new VallaResponseDto(i + 1, equipo.club.getId(), equipo.club.getNombreClub(),
                    equipo.jugados, equipo.golesEnContra, Math.round(equipo.promedio() * 100) / 100.0,
                    equipo.invictas));
        }
        return filas;
    }

    /** Menos puntos de disciplina primero; a igual puntaje, el que jugó más. */
    private List<FairPlayResponseDto> fairPlay(Collection<Equipo> equipos) {
        ToIntFunction<Equipo> puntos = e -> e.amarillas * PUNTOS_AMARILLA + e.rojas * PUNTOS_ROJA;
        List<Equipo> ordenados = equipos.stream()
                .sorted(Comparator.comparingInt(puntos)
                        .thenComparing(Comparator.comparingInt((Equipo e) -> e.jugados).reversed())
                        .thenComparing(e -> e.club.getId()))
                .toList();
        List<FairPlayResponseDto> filas = new ArrayList<>();
        for (int i = 0; i < ordenados.size(); i++) {
            Equipo equipo = ordenados.get(i);
            filas.add(new FairPlayResponseDto(i + 1, equipo.club.getId(), equipo.club.getNombreClub(),
                    equipo.jugados, equipo.amarillas, equipo.rojas, puntos.applyAsInt(equipo)));
        }
        return filas;
    }

    private int contar(List<EventoPartido> eventosDelJugador, Predicate<EventoPartido> cuenta) {
        return (int) eventosDelJugador.stream().filter(cuenta).count();
    }

    private int goles(Short goles) {
        return goles == null ? 0 : goles;
    }

    /** Lo que se acumula de un equipo para las vallas y el fair play. */
    private static final class Equipo {
        private final Club club;
        private int jugados;
        private int golesEnContra;
        private int invictas;
        private int amarillas;
        private int rojas;

        private Equipo(Club club) {
            this.club = club;
        }

        private void jugo(int enContra) {
            jugados++;
            golesEnContra += enContra;
            if (enContra == 0) {
                invictas++;
            }
        }

        private double promedio() {
            return jugados == 0 ? 0 : (double) golesEnContra / jugados;
        }
    }
}

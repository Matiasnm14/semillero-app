package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.response.torneos.ComparacionJugadoresResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.EstadisticasJugadorResponseDto;
import edu.upb.semillero.repository.entity.ParticipacionPartido;
import edu.upb.semillero.repository.entity.torneos.EventoPartido;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.torneos.TipoEventoPartido;
import edu.upb.semillero.repository.enums.torneos.TipoGol;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.torneos.EstadisticasJugadorRepository;
import edu.upb.semillero.repository.repos.torneos.EventoEstadisticasRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Estadísticas de planillas y eventos, independientes del análisis de video. */
@Service
@Transactional(readOnly = true)
public class EstadisticasJugadorService {
    private final JugadorRepository jugadores;
    private final TorneoRepository torneos;
    private final EstadisticasJugadorRepository participaciones;
    private final EventoEstadisticasRepository eventos;

    public EstadisticasJugadorService(JugadorRepository jugadores, TorneoRepository torneos,
            EstadisticasJugadorRepository participaciones, EventoEstadisticasRepository eventos) {
        this.jugadores = jugadores;
        this.torneos = torneos;
        this.participaciones = participaciones;
        this.eventos = eventos;
    }

    public EstadisticasJugadorResponseDto forPlayer(Long jugadorId, Integer temporada, Long torneoId) {
        return summarize(List.of(jugadorId), temporada, torneoId).get(jugadorId);
    }

    public ComparacionJugadoresResponseDto compare(Long jugadorAId, Long jugadorBId,
            Integer temporada, Long torneoId) {
        var summary = summarize(Stream.of(jugadorAId, jugadorBId).distinct().toList(), temporada, torneoId);
        return new ComparacionJugadoresResponseDto(summary.get(jugadorAId), summary.get(jugadorBId));
    }

    private Map<Long, EstadisticasJugadorResponseDto> summarize(List<Long> ids, Integer temporada, Long torneoId) {
        Map<Long, Totals> totals = new HashMap<>();
        for (Long id : ids) {
            if (!jugadores.existsById(id)) throw new NotFoundException("Jugador con id " + id + " no existe");
            totals.put(id, new Totals());
        }
        if (torneoId != null && !torneos.existsById(torneoId)) {
            throw new NotFoundException("Torneo con id " + torneoId + " no existe");
        }
        var entries = participaciones.findParticipations(ids, EstadoPartido.FINALIZADO, temporada, torneoId);
        var goals = entries.isEmpty() ? List.<EventoPartido>of()
                : eventos.findGoals(entries.stream().map(ParticipacionPartido::getId).toList(), TipoEventoPartido.GOL);

        // Un gol o asistencia válida también acredita participación si faltan los minutos.
        Set<Long> goalParticipants = new HashSet<>();
        for (var goal : goals) {
            if (!validAuthor(goal)) continue;
            goalParticipants.add(goal.getParticipacion().getId());
            if (goal.getTipoGol() != TipoGol.AUTOGOL && validAssistant(goal)) {
                goalParticipants.add(goal.getParticipacionRelacionada().getId());
            }
        }
        Set<Long> played = new HashSet<>();
        for (var entry : entries) {
            Long minutes = minutes(entry);
            boolean appeared = Boolean.TRUE.equals(entry.getEsTitular())
                    || minutes != null && minutes > 0 || entry.getMinutoEntrada() > 0
                    || goalParticipants.contains(entry.getId());
            if (!appeared) continue;
            played.add(entry.getId());
            var total = totals.get(entry.getJugador().getId());
            total.matches++;
            if (minutes == null) total.completeMinutes = false;
            else total.knownMinutes += minutes;
            // La consulta está ordenada: conservar el último dorsal real, nunca inventar #0.
            if (total.jersey == null && entry.getDorsal() != null && entry.getDorsal() > 0) {
                total.jersey = entry.getDorsal();
                total.jerseyMatchId = entry.getPartido().getId();
                total.jerseyClubId = entry.getClub().getId();
            }
        }
        for (var goal : goals) {
            if (!validAuthor(goal) || goal.getTipoGol() == TipoGol.AUTOGOL) continue;
            var author = goal.getParticipacion();
            if (played.contains(author.getId())) totals.get(author.getJugador().getId()).goals++;
            if (validAssistant(goal)) {
                var assistant = goal.getParticipacionRelacionada();
                if (played.contains(assistant.getId())) totals.get(assistant.getJugador().getId()).assists++;
            }
        }
        Map<Long, EstadisticasJugadorResponseDto> result = new HashMap<>();
        totals.forEach((id, total) -> result.put(id, new EstadisticasJugadorResponseDto(id, temporada, torneoId,
                total.matches, total.goals, total.assists, total.completeMinutes ? total.knownMinutes : null,
                total.knownMinutes, total.completeMinutes, total.jersey, total.jerseyMatchId, total.jerseyClubId, null)));
        return result;
    }

    private Long minutes(ParticipacionPartido entry) {
        if (entry.getMinutosJugados() != null) return entry.getMinutosJugados().longValue();
        if (entry.getMinutoSalida() == null) return null;
        int difference = entry.getMinutoSalida() - entry.getMinutoEntrada();
        return difference >= 0 ? (long) difference : null;
    }

    private boolean validAuthor(EventoPartido goal) {
        var author = goal.getParticipacion();
        return goal.getTipoGol() != null && author.getPartido().getId().equals(goal.getPartido().getId())
                && (goal.getTipoGol() == TipoGol.AUTOGOL || author.getClub().getId().equals(goal.getClub().getId()));
    }

    private boolean validAssistant(EventoPartido goal) {
        var assistant = goal.getParticipacionRelacionada();
        return assistant != null && assistant.getPartido().getId().equals(goal.getPartido().getId())
                && assistant.getClub().getId().equals(goal.getParticipacion().getClub().getId());
    }

    private static class Totals {
        long matches;
        long goals;
        long assists;
        long knownMinutes;
        boolean completeMinutes = true;
        Short jersey;
        Long jerseyMatchId;
        Long jerseyClubId;
    }
}

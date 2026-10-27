package edu.upb.semillero.service;

import edu.upb.semillero.repository.entity.AnalysisJob;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.repos.AnalysisJobRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.ParticipacionPartidoRepository;
import edu.upb.semillero.repository.repos.PartidoRepository;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SB-052: validates ownership only. Completion and publication belong to SB-051. */
@Service
@Transactional(readOnly = true)
public class PlayerMatchMetricsIntegrityService {
    private final AnalysisJobRepository analyses;
    private final PartidoRepository partidos;
    private final JugadorRepository jugadores;
    private final ParticipacionPartidoRepository participaciones;

    public record References(AnalysisJob analysis, Partido partido, Jugador jugador) {}

    public PlayerMatchMetricsIntegrityService(AnalysisJobRepository analyses, PartidoRepository partidos,
            JugadorRepository jugadores, ParticipacionPartidoRepository participaciones) {
        this.analyses = analyses;
        this.partidos = partidos;
        this.jugadores = jugadores;
        this.participaciones = participaciones;
    }

    public References validate(Long analysisId, Long partidoId, Long jugadorId) {
        requirePositive(analysisId, "analysisId");
        requirePositive(partidoId, "partidoId");
        requirePositive(jugadorId, "jugadorId");

        var analysis = analyses.findById(analysisId)
                .orElseThrow(() -> new NotFoundException("Análisis con id " + analysisId + " no existe"));
        var partido = partidos.findById(partidoId)
                .orElseThrow(() -> new NotFoundException("Partido con id " + partidoId + " no existe"));
        var jugador = jugadores.findById(jugadorId)
                .orElseThrow(() -> new NotFoundException("Jugador con id " + jugadorId + " no existe"));

        if (!analysis.getVideo().getPartido().getId().equals(partidoId)) {
            throw new BusinessException("El análisis no corresponde al partido indicado");
        }
        var participacion = participaciones.findByPartidoIdAndJugadorId(partidoId, jugadorId)
                .orElseThrow(() -> new BusinessException("El jugador no está registrado en el partido indicado"));
        Long clubId = participacion.getClub().getId();
        if (!clubId.equals(partido.getClubLocal().getId()) && !clubId.equals(partido.getClubVisitante().getId())) {
            throw new BusinessException("La participación del jugador no corresponde a un club del partido");
        }
        return new References(analysis, partido, jugador);
    }

    private void requirePositive(Long id, String field) {
        if (id == null || id <= 0) throw new BusinessException(field + " debe ser un identificador positivo");
    }
}

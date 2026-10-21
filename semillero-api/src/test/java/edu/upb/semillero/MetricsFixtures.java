package edu.upb.semillero;

import edu.upb.semillero.repository.entity.*;
import edu.upb.semillero.repository.enums.*;
import edu.upb.semillero.repository.repos.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationContext;

final class MetricsFixtures {
    record Fixture(AnalysisJob analysis, Partido partido, Partido otroPartido,
            Jugador jugador, Jugador otroJugador, Club tercero, ParticipacionPartido participacion) {}

    static Fixture create(ApplicationContext context) {
        var clubs = context.getBean(ClubRepository.class);
        var local = new Club(); local.setNombreClub("Local");
        var visitor = new Club(); visitor.setNombreClub("Visitante");
        var third = new Club(); third.setNombreClub("Tercero");
        clubs.saveAndFlush(local); clubs.saveAndFlush(visitor); clubs.saveAndFlush(third);
        var matches = context.getBean(PartidoRepository.class);
        Partido match = match(local, visitor);
        Partido other = match(local, visitor);
        matches.saveAndFlush(match); matches.saveAndFlush(other);
        var players = context.getBean(JugadorRepository.class);
        Jugador player = players.saveAndFlush(new Jugador());
        Jugador otherPlayer = players.saveAndFlush(new Jugador());
        ParticipacionPartido participation = new ParticipacionPartido();
        participation.setPartido(match); participation.setJugador(player); participation.setClub(local);
        participation.setEsTitular(true); participation.setMinutoEntrada((short) 0);
        context.getBean(ParticipacionPartidoRepository.class).saveAndFlush(participation);
        VideoPartido video = new VideoPartido();
        video.setPartido(match); video.setStorageId(UUID.randomUUID().toString());
        video.setNombreOriginal("fixture.mp4"); video.setFormato("mp4"); video.setTamanoBytes(100L);
        video.setDuracionSegundos(90.0); video.setSha256("a".repeat(64)); video.setFechaCarga(Instant.now());
        video.setTituloYoutube("2026-10-21"); video.setEstadoYoutube(EstadoYoutube.DESHABILITADO);
        context.getBean(VideoPartidoRepository.class).saveAndFlush(video);
        Instant now = Instant.parse("2026-10-21T12:00:00Z");
        AnalysisJob analysis = new AnalysisJob();
        analysis.setVideo(video); analysis.setEstado(EstadoAnalisis.COMPLETADO);
        analysis.setFechaSolicitud(now); analysis.setFechaInicio(now.plusSeconds(1));
        analysis.setFechaFin(now.plusSeconds(2)); analysis.setFechaActualizacion(now.plusSeconds(2));
        context.getBean(AnalysisJobRepository.class).saveAndFlush(analysis);
        return new Fixture(analysis, match, other, player, otherPlayer, third, participation);
    }

    static PlayerMatchMetrics metric(Fixture fixture) {
        return new PlayerMatchMetrics(fixture.analysis(), fixture.partido(), fixture.jugador(),
                new BigDecimal("1234.567890"), new BigDecimal("3.000001"), new BigDecimal("9.123456"),
                new BigDecimal("0.123456"), new BigDecimal("60.123456"), 1, false);
    }

    private static Partido match(Club local, Club visitor) {
        Partido match = new Partido();
        match.setClubLocal(local); match.setClubVisitante(visitor);
        match.setFechaInicio(LocalDateTime.parse("2026-10-21T10:00:00"));
        match.setEstado(EstadoPartido.FINALIZADO);
        return match;
    }
}

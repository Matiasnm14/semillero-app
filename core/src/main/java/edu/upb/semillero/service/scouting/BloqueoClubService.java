package edu.upb.semillero.service.scouting;

import edu.upb.semillero.repository.repos.ClubRepository;
import edu.upb.semillero.repository.entity.scouting.BloqueoClub;
import edu.upb.semillero.repository.repos.scouting.*;
import edu.upb.semillero.service.exception.*;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class BloqueoClubService {
    private final PreferenciaContactoService perfiles;
    private final PreferenciaContactoRepository preferencias;
    private final BloqueoClubRepository bloqueos;
    private final ClubRepository clubes;
    private final Clock clock;

    public BloqueoClubService(PreferenciaContactoService perfiles, PreferenciaContactoRepository preferencias,
            BloqueoClubRepository bloqueos, ClubRepository clubes, @Qualifier("scoutingClock") Clock clock) {
        this.perfiles = perfiles; this.preferencias = preferencias;
        this.bloqueos = bloqueos; this.clubes = clubes; this.clock = clock;
    }

    public record Bloqueado(Long clubId, Instant bloqueadoEn) {}

    public Page<Bloqueado> listar(int page, int size) {
        return bloqueos.findByJugadorIdAndActivoTrue(perfiles.titular().getId(),
                DirectorioScoutingService.pagina(page, size, "bloqueadoEn"))
                .map(b -> new Bloqueado(b.getClubId(), b.getBloqueadoEn()));
    }

    @Transactional
    public void cambiar(Long clubId, boolean activo) {
        Long jugadorId = perfiles.titular().getId();
        // Same lock as contact acceptance and sensitive reads: no block can be lost
        // against concurrent requests. Users opt into scouting before managing contacts.
        preferencias.bloquear(jugadorId).orElseThrow(() ->
                new ConflictException("Primero guarda tus preferencias de scouting"));
        if (!clubes.existsById(clubId)) throw new NotFoundException("Club no disponible");
        String id = jugadorId + ":" + clubId;
        var previous = bloqueos.findById(id);
        if (previous.isEmpty() && !activo) return;
        var b = previous.orElseGet(() -> {
            var nuevo = new BloqueoClub(); nuevo.setId(id); nuevo.setJugadorId(jugadorId);
            nuevo.setClubId(clubId); return nuevo;
        });
        if (previous.isPresent() && b.isActivo() == activo) return;
        b.setActivo(activo);
        if (activo) { b.setBloqueadoEn(clock.instant()); b.setDesbloqueadoEn(null); }
        else b.setDesbloqueadoEn(clock.instant());
        bloqueos.saveAndFlush(b);
    }

    public void exigirDisponible(Long jugadorId, Long clubId) {
        if (bloqueos.findById(jugadorId + ":" + clubId).map(BloqueoClub::isActivo).orElse(false)) {
            throw new NotFoundException("Perfil no disponible para contacto");
        }
    }

    public void exigirAutorizacion(Long jugadorId, Long clubId, Instant solicitudCreadaEn) {
        var b = bloqueos.findById(jugadorId + ":" + clubId);
        // Unblocking permits a NEW request; never restores previously shared contact data.
        if (b.isPresent() && (b.get().isActivo() || !solicitudCreadaEn.isAfter(b.get().getBloqueadoEn()))) {
            throw new ConflictException("El contacto ya no está habilitado");
        }
    }
}

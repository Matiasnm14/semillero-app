package edu.upb.semillero.service.scouting;

import edu.upb.semillero.repository.dto.response.scouting.GuardadoDto;
import edu.upb.semillero.repository.entity.scouting.JugadorGuardado;
import edu.upb.semillero.repository.repos.scouting.*;
import edu.upb.semillero.service.exception.NotFoundException;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class JugadorGuardadoService {
    private final AccesoScouting acceso;
    private final DirectorioScoutingService directorio;
    private final PreferenciaContactoRepository preferencias;
    private final JugadorGuardadoRepository guardados;
    private final Clock clock;
    public JugadorGuardadoService(AccesoScouting acceso, DirectorioScoutingService directorio,
            PreferenciaContactoRepository preferencias, JugadorGuardadoRepository guardados,
            @Qualifier("scoutingClock") Clock clock) {
        this.acceso = acceso; this.directorio = directorio; this.preferencias = preferencias;
        this.guardados = guardados; this.clock = clock;
    }
    @Transactional
    public GuardadoDto guardar(Long clubId, Long jugadorId) {
        acceso.exigirClub(clubId);
        preferencias.bloquear(jugadorId).orElseThrow(() -> new NotFoundException("Perfil no disponible"));
        directorio.jugador(jugadorId);
        var g = guardados.findByClubIdAndJugadorId(clubId, jugadorId).orElseGet(() -> {
            var nuevo = new JugadorGuardado(); nuevo.setClubId(clubId); nuevo.setJugadorId(jugadorId);
            nuevo.setCreadoPor(acceso.usuario()); nuevo.setCreadoEn(clock.instant());
            return guardados.saveAndFlush(nuevo);
        });
        return dto(g);
    }
    public Page<GuardadoDto> listar(Long clubId, int page, int size) {
        acceso.exigirClub(clubId);
        return guardados.findByClubId(clubId, DirectorioScoutingService.pagina(page, size, "creadoEn")).map(this::dto);
    }
    @Transactional
    public void quitar(Long clubId, Long jugadorId) {
        acceso.exigirClub(clubId);
        guardados.findByClubIdAndJugadorId(clubId, jugadorId).ifPresent(guardados::delete);
    }
    private GuardadoDto dto(JugadorGuardado g) { return new GuardadoDto(g.getClubId(), g.getJugadorId(), g.getCreadoEn()); }
}

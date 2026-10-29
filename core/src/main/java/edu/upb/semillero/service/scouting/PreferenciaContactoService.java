package edu.upb.semillero.service.scouting;

import edu.upb.semillero.repository.dto.request.scouting.PreferenciaContactoDto;
import edu.upb.semillero.repository.dto.response.scouting.PreferenciaActualDto;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.scouting.PreferenciaContacto;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.scouting.PreferenciaContactoRepository;
import edu.upb.semillero.service.exception.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class PreferenciaContactoService {
    private final AccesoScouting acceso;
    private final JugadorRepository jugadores;
    private final PreferenciaContactoRepository preferencias;
    private final Clock clock;
    public PreferenciaContactoService(AccesoScouting acceso, JugadorRepository jugadores,
            PreferenciaContactoRepository preferencias, @Qualifier("scoutingClock") Clock clock) {
        this.acceso = acceso; this.jugadores = jugadores; this.preferencias = preferencias; this.clock = clock;
    }
    public Jugador titular() {
        return jugadores.findFirstByUsuarioId(acceso.usuario())
            .orElseThrow(() -> new ForbiddenException("La cuenta no tiene una ficha de jugador"));
    }
    public PreferenciaActualDto obtener() {
        var j = titular();
        return preferencias.findById(j.getId()).map(this::dto)
            .orElse(new PreferenciaActualDto(j.getId(), false, false, null, 0));
    }
    @Transactional
    public PreferenciaActualDto actualizar(PreferenciaContactoDto input) {
        var j = titular();
        if ((input.visible() || input.aceptaSolicitudes()) && !adulto(j))
            throw new BusinessException("El contacto de menores o edad desconocida requiere el flujo de tutor, pendiente de implementación");
        if (input.aceptaSolicitudes() && !input.visible())
            throw new BusinessException("Para recibir solicitudes el perfil debe estar visible");
        var p = preferencias.bloquear(j.getId()).orElseGet(() -> {
            var nueva = new PreferenciaContacto(); nueva.setJugadorId(j.getId()); return nueva;
        });
        if (input.version() != p.getVersion()) throw new ConflictException("Las preferencias cambiaron; vuelve a consultarlas");
        p.setVisible(input.visible()); p.setAceptaSolicitudes(input.aceptaSolicitudes());
        p.setActualizadaEn(clock.instant());
        return dto(preferencias.saveAndFlush(p));
    }
    public boolean adulto(Jugador j) {
        return j.getFechaNacimiento() != null && !j.getFechaNacimiento().isAfter(LocalDate.now(clock).minusYears(18));
    }
    private PreferenciaActualDto dto(PreferenciaContacto p) {
        return new PreferenciaActualDto(p.getJugadorId(), p.isVisible(), p.isAceptaSolicitudes(), p.getActualizadaEn(), p.getVersion());
    }
}

package edu.upb.semillero.service.scouting;

import edu.upb.semillero.repository.dto.response.scouting.DatosContactoDto;
import edu.upb.semillero.repository.entity.scouting.AccesoContacto;
import edu.upb.semillero.repository.enums.scouting.EstadoContacto;
import edu.upb.semillero.repository.repos.*;
import edu.upb.semillero.repository.repos.scouting.*;
import edu.upb.semillero.service.comun.ComunService;
import edu.upb.semillero.service.exception.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatosContactoService {
    private final SolicitudContactoService solicitudes;
    private final SolicitudContactoRepository repository;
    private final PreferenciaContactoRepository preferencias;
    private final PreferenciaContactoService politica;
    private final JugadorRepository jugadores;
    private final UsuarioRepository usuarios;
    private final ComunService comun;
    private final AccesoScouting acceso;
    private final AccesoContactoRepository auditoria;
    private final Clock clock;
    public DatosContactoService(SolicitudContactoService solicitudes, SolicitudContactoRepository repository,
            PreferenciaContactoRepository preferencias, PreferenciaContactoService politica,
            JugadorRepository jugadores, UsuarioRepository usuarios, ComunService comun,
            AccesoScouting acceso, AccesoContactoRepository auditoria, @Qualifier("scoutingClock") Clock clock) {
        this.solicitudes = solicitudes; this.repository = repository; this.preferencias = preferencias;
        this.politica = politica; this.jugadores = jugadores; this.usuarios = usuarios; this.comun = comun;
        this.acceso = acceso; this.auditoria = auditoria; this.clock = clock;
    }
    @Transactional
    public DatosContactoDto consultar(UUID id, String proposito) {
        solicitudes.obtener(id);
        var s = repository.bloquear(id).orElseThrow(() -> new NotFoundException("Solicitud no disponible"));
        if (s.getEstado() != EstadoContacto.ACEPTADA) throw new ConflictException("El jugador debe aceptar primero la solicitud");
        var p = preferencias.bloquear(s.getJugadorId());
        var j = jugadores.findById(s.getJugadorId());
        if (p.isEmpty() || !p.get().isVisible() || !p.get().isAceptaSolicitudes()
                || j.isEmpty() || !politica.adulto(j.get()) || j.get().getUsuario() == null
                || j.get().getEstado() != edu.upb.semillero.repository.enums.EstadoJugador.ACTIVO
                || !comun.clubesDondeTrabaja(s.getSolicitanteId()).contains(s.getClubId()))
            throw new ConflictException("El contacto ya no está habilitado");
        var representante = usuarios.findById(s.getSolicitanteId())
            .orElseThrow(() -> new ConflictException("El representante ya no está disponible"));
        if (j.get().getUsuario().getEmail() == null || j.get().getUsuario().getEmail().isBlank()
                || representante.getEmail() == null || representante.getEmail().isBlank())
            throw new ConflictException("Ambas cuentas deben completar su correo antes de consultar el contacto");
        var evento = new AccesoContacto(); evento.setSolicitud(s); evento.setActorId(acceso.usuario());
        evento.setProposito(proposito.strip()); evento.setFecha(clock.instant()); auditoria.saveAndFlush(evento);
        return new DatosContactoDto(j.get().getUsuario().getEmail(), representante.getEmail());
    }
}

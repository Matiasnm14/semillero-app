package edu.upb.semillero.service.scouting;

import edu.upb.semillero.repository.dto.request.scouting.*;
import edu.upb.semillero.repository.dto.response.scouting.*;
import edu.upb.semillero.repository.entity.scouting.*;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.scouting.*;
import edu.upb.semillero.repository.enums.scouting.EstadoContacto;
import edu.upb.semillero.service.exception.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class SolicitudContactoService {
    private final AccesoScouting acceso;
    private final JugadorRepository jugadores;
    private final PreferenciaContactoRepository preferencias;
    private final PreferenciaContactoService politica;
    private final SolicitudContactoRepository solicitudes;
    private final EventoContactoRepository eventos;
    private final CorreoContactoRepository correos;
    private final Clock clock;
    public SolicitudContactoService(AccesoScouting acceso, JugadorRepository jugadores,
            PreferenciaContactoRepository preferencias, PreferenciaContactoService politica,
            SolicitudContactoRepository solicitudes, EventoContactoRepository eventos,
            CorreoContactoRepository correos, @Qualifier("scoutingClock") Clock clock) {
        this.acceso = acceso; this.jugadores = jugadores; this.preferencias = preferencias;
        this.politica = politica; this.solicitudes = solicitudes; this.eventos = eventos;
        this.correos = correos; this.clock = clock;
    }
    public record Creada(SolicitudContactoDto solicitud, boolean nueva) {}

    @Transactional
    public Creada crear(UUID clave, CrearContactoDto input) {
        acceso.exigirClub(input.clubId());
        Long actor = acceso.usuario();
        String motivo = input.motivo().strip();
        var previa = solicitudes.findBySolicitanteIdAndClave(actor, clave);
        if (previa.isPresent()) return repetir(previa.get(), input, motivo);
        // Todos los creadores del mismo par se serializan sobre nuestra preferencia,
        // sin bloquear ni modificar tablas del grupo de ficha técnica.
        var p = preferencias.bloquear(input.jugadorId())
            .orElseThrow(() -> new NotFoundException("Perfil no disponible para contacto"));
        var jugador = jugadores.findById(input.jugadorId())
            .orElseThrow(() -> new NotFoundException("Perfil no disponible para contacto"));
        if (!p.isVisible() || !p.isAceptaSolicitudes() || !politica.adulto(jugador)
                || jugador.getEstado() != edu.upb.semillero.repository.enums.EstadoJugador.ACTIVO
                || jugador.getUsuario() == null)
            throw new NotFoundException("Perfil no disponible para contacto");
        if (actor.equals(jugador.getUsuario().getId())) throw new BusinessException("No puedes contactarte a ti mismo");
        previa = solicitudes.findBySolicitanteIdAndClave(actor, clave);
        if (previa.isPresent()) return repetir(previa.get(), input, motivo);
        if (solicitudes.existsByClubIdAndJugadorIdAndEstado(input.clubId(), input.jugadorId(), EstadoContacto.PENDIENTE))
            throw new ConflictException("Ya existe una solicitud pendiente de este club al jugador");
        if (solicitudes.existsByClubIdAndJugadorIdAndCreadaEnAfter(input.clubId(), input.jugadorId(), clock.instant().minus(Duration.ofDays(7))))
            throw new ConflictException("Debes esperar siete días entre solicitudes del mismo club al jugador");
        var s = new SolicitudContacto();
        s.setClubId(input.clubId()); s.setJugadorId(input.jugadorId()); s.setSolicitanteId(actor);
        s.setClave(clave); s.setMotivo(motivo); s.setCreadaEn(clock.instant()); s.setActualizadaEn(clock.instant());
        solicitudes.saveAndFlush(s);
        var evento = registrar(s, actor, null, "Solicitud creada");
        encolar(evento, jugador.getUsuario().getId(), "INVITACION_JUGADOR");
        encolar(evento, actor, "CONSTANCIA_CLUB");
        return new Creada(dto(s), true);
    }
    private Creada repetir(SolicitudContacto s, CrearContactoDto input, String motivo) {
        if (!s.getClubId().equals(input.clubId()) || !s.getJugadorId().equals(input.jugadorId()) || !s.getMotivo().equals(motivo))
            throw new ConflictException("La clave de idempotencia ya fue utilizada con otros datos");
        return new Creada(dto(s), false);
    }
    public Page<SolicitudContactoDto> bandeja(EstadoContacto estado, int page, int size) {
        var clubes = acceso.clubes();
        var idsJugador = jugadores.findFirstByUsuarioId(acceso.usuario()).map(j -> Set.of(j.getId())).orElse(Set.of(-1L));
        return solicitudes.bandeja(clubes.isEmpty() ? Set.of(-1L) : clubes, idsJugador, estado,
                DirectorioScoutingService.pagina(page, size, "creadaEn")).map(this::dto);
    }
    public SolicitudContactoDto obtener(UUID id) { return dto(permitida(id)); }
    private SolicitudContacto permitida(UUID id) {
        var s = solicitudes.findById(id).orElseThrow(() -> new NotFoundException("Solicitud no disponible"));
        verificar(s); return s;
    }
    private boolean esJugador(SolicitudContacto s) {
        return jugadores.findFirstByUsuarioId(acceso.usuario()).map(j -> j.getId().equals(s.getJugadorId())).orElse(false);
    }
    private void verificar(SolicitudContacto s) {
        if (!esJugador(s) && !acceso.clubes().contains(s.getClubId())) throw new NotFoundException("Solicitud no disponible");
    }
    @Transactional
    public SolicitudContactoDto resolver(UUID id, ResolverContactoDto input) {
        // La comprobación de acceso precede al bloqueo y evita exponer existencia.
        permitida(id);
        var s = solicitudes.bloquear(id).orElseThrow(() -> new NotFoundException("Solicitud no disponible"));
        verificar(s);
        if (s.getVersion() != input.version()) throw new ConflictException("La solicitud cambió; consulta su versión actual");
        if (s.getEstado() != EstadoContacto.PENDIENTE) throw new ConflictException("La solicitud ya fue resuelta");
        var destino = input.estado();
        if (destino == EstadoContacto.CANCELADA) {
            acceso.exigirClub(s.getClubId());
        } else if (destino == EstadoContacto.ACEPTADA || destino == EstadoContacto.RECHAZADA) {
            if (!esJugador(s)) throw new ForbiddenException("Solo el jugador destinatario puede responder");
            if (destino == EstadoContacto.ACEPTADA) {
                var p = preferencias.bloquear(s.getJugadorId());
                var j = jugadores.findById(s.getJugadorId());
                if (p.isEmpty() || !p.get().isVisible() || !p.get().isAceptaSolicitudes()
                        || j.isEmpty() || !politica.adulto(j.get())
                        || j.get().getEstado() != edu.upb.semillero.repository.enums.EstadoJugador.ACTIVO)
                    throw new BusinessException("El perfil ya no permite contacto");
            }
        } else throw new BusinessException("La respuesta debe ser ACEPTADA, RECHAZADA o CANCELADA");
        var anterior = s.getEstado(); s.setEstado(destino); s.setActualizadaEn(clock.instant());
        solicitudes.flush();
        var evento = registrar(s, acceso.usuario(), anterior, input.motivo().strip());
        encolar(evento, s.getSolicitanteId(), "RESULTADO_SOLICITUD");
        return dto(s);
    }
    public Page<EventoContactoDto> historial(UUID id, int page, int size) {
        permitida(id);
        return eventos.findBySolicitudId(id, DirectorioScoutingService.pagina(page, size, "fecha"))
            .map(e -> new EventoContactoDto(e.getId(), e.getActorId(), e.getAnterior(), e.getNuevo(), e.getMotivo(), e.getFecha()));
    }
    private EventoContacto registrar(SolicitudContacto s, Long actor, EstadoContacto anterior, String motivo) {
        var e = new EventoContacto(); e.setSolicitud(s); e.setActorId(actor); e.setAnterior(anterior);
        e.setNuevo(s.getEstado()); e.setMotivo(motivo); e.setFecha(clock.instant());
        return eventos.saveAndFlush(e);
    }
    private void encolar(EventoContacto evento, Long destinatario, String plantilla) {
        var correo = new CorreoContacto(); correo.setEvento(evento); correo.setDestinatarioId(destinatario);
        correo.setPlantilla(plantilla); correo.setCreadaEn(clock.instant()); correos.save(correo);
    }
    private SolicitudContactoDto dto(SolicitudContacto s) {
        return new SolicitudContactoDto(s.getId(), s.getClubId(), s.getJugadorId(), s.getSolicitanteId(),
            s.getMotivo(), s.getEstado(), s.getCreadaEn(), s.getActualizadaEn(), s.getVersion());
    }
}

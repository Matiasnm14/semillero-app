package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.RespuestaSolicitudRequestDto;
import edu.upb.semillero.repository.dto.request.SolicitudContactoRequestDto;
import edu.upb.semillero.repository.dto.response.SolicitudContactoResponseDto;
import edu.upb.semillero.repository.entity.*;
import edu.upb.semillero.repository.enums.EstadoSolicitudContacto;
import edu.upb.semillero.repository.repos.SolicitudContactoRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.UsuarioClubRepository;
import edu.upb.semillero.repository.repos.UsuarioTrabajaRepository;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ForbiddenException;
import edu.upb.semillero.service.exception.NotFoundException;
import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SolicitudContactoService {
    private final SolicitudContactoRepository repository;
    private final UsuarioClubRepository usuarioClubs;
    private final UsuarioTrabajaRepository usuarioTrabaja;
    private final JugadorRepository jugadores;
    private final EntityRelations relations;
    private final NotificacionService notificaciones;

    public SolicitudContactoService(SolicitudContactoRepository repository,
            UsuarioClubRepository usuarioClubs, UsuarioTrabajaRepository usuarioTrabaja,
            JugadorRepository jugadores, EntityRelations relations, NotificacionService notificaciones) {
        this.repository = repository;
        this.usuarioClubs = usuarioClubs;
        this.usuarioTrabaja = usuarioTrabaja;
        this.jugadores = jugadores;
        this.relations = relations;
        this.notificaciones = notificaciones;
    }

    @Transactional
    public SolicitudContactoResponseDto crear(SolicitudContactoRequestDto request, UsuarioAutenticado actor) {
        if (actor == null || actor.clubId() == null) {
            throw new ForbiddenException("La solicitud debe enviarse desde una cuenta vinculada a un club");
        }
        UsuarioClub cuentaClub = usuarioClubs.findFirstByUsuarioId(actor.usuarioId())
                .orElseThrow(() -> new ForbiddenException("La cuenta no tiene un perfil de club asociado"));
        if (!usuarioTrabaja.existsByUsuarioClubIdAndClubId(cuentaClub.getId(), actor.clubId())) {
            throw new ForbiddenException("La cuenta no pertenece al club indicado por su sesión");
        }
        Jugador jugador = relations.find(Jugador.class, request.jugadorId());
        SolicitudContacto entity = new SolicitudContacto();
        entity.setClub(relations.find(Club.class, actor.clubId()));
        entity.setJugador(jugador);
        entity.setUsuarioClub(cuentaClub);
        entity.setAsunto(request.asunto().trim());
        entity.setMensaje(request.mensaje().trim());
        entity.setEstado(EstadoSolicitudContacto.PENDIENTE);
        entity.setFechaEnvio(LocalDateTime.now());
        entity = repository.saveAndFlush(entity);
        Usuario destino = jugador.getUsuario();
        notificaciones.crear(destino == null ? null : destino.getId(), "SOLICITUD_CONTACTO",
                "Nueva solicitud de contacto", "Un club quiere ponerse en contacto contigo.",
                entity.getId(), "SOLICITUD_CONTACTO");
        return toResponse(entity);
    }

    public Page<SolicitudContactoResponseDto> delClub(Long clubId, UsuarioAutenticado actor, Pageable pageable) {
        exigirAdminOMismoClub(actor, clubId);
        return repository.findByClubIdOrderByFechaEnvioDesc(clubId, pageable).map(this::toResponse);
    }

    @Transactional
    public Page<SolicitudContactoResponseDto> delJugador(Long jugadorId, UsuarioAutenticado actor, Pageable pageable) {
        boolean admin = esAdmin(actor);
        if (!admin) {
            Long propio = actor == null ? null : relacionesJugador(actor.usuarioId());
            if (!jugadorId.equals(propio)) throw new ForbiddenException("Solo puedes ver tus solicitudes recibidas");
        }
        Page<SolicitudContacto> page = repository.findByJugadorIdOrderByFechaEnvioDesc(jugadorId, pageable);
        page.getContent().stream().filter(s -> s.getEstado() == EstadoSolicitudContacto.PENDIENTE)
                .forEach(s -> s.setEstado(EstadoSolicitudContacto.VISTO));
        return page.map(this::toResponse);
    }

    @Transactional
    public SolicitudContactoResponseDto responder(Long id, RespuestaSolicitudRequestDto request,
            UsuarioAutenticado actor) {
        SolicitudContacto entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Solicitud de contacto con id " + id + " no existe"));
        if (!esAdmin(actor) && !entity.getJugador().getUsuario().getId().equals(actor.usuarioId())) {
            throw new ForbiddenException("Solo el jugador destinatario puede responder esta solicitud");
        }
        if (entity.getEstado() != EstadoSolicitudContacto.PENDIENTE
                && entity.getEstado() != EstadoSolicitudContacto.VISTO) {
            throw new BusinessException("La solicitud ya fue respondida");
        }
        if (request.estado() != EstadoSolicitudContacto.RESPONDIDO
                && request.estado() != EstadoSolicitudContacto.ACEPTADO
                && request.estado() != EstadoSolicitudContacto.RECHAZADO) {
            throw new BusinessException("La respuesta debe ser RESPONDIDO, ACEPTADO o RECHAZADO");
        }
        entity.setEstado(request.estado());
        entity.setRespuesta(request.respuesta() == null ? null : request.respuesta().trim());
        entity.setFechaRespuesta(LocalDateTime.now());
        entity = repository.saveAndFlush(entity);
        notificaciones.crear(entity.getUsuarioClub() == null || entity.getUsuarioClub().getUsuario() == null
                        ? null : entity.getUsuarioClub().getUsuario().getId(),
                "RESPUESTA_CONTACTO", "Respuesta a solicitud de contacto",
                "El jugador respondió a tu solicitud de contacto.", entity.getId(), "SOLICITUD_CONTACTO");
        return toResponse(entity);
    }

    private Long relacionesJugador(Long usuarioId) {
        return usuarioId == null ? null : jugadores.findFirstByUsuarioId(usuarioId)
                .map(Jugador::getId).orElse(null);
    }

    private void exigirAdminOMismoClub(UsuarioAutenticado actor, Long clubId) {
        if (esAdmin(actor)) return;
        if (actor == null || !clubId.equals(actor.clubId())) {
            throw new ForbiddenException("Solo puedes ver las solicitudes enviadas por tu club");
        }
        UsuarioClub perfil = usuarioClubs.findFirstByUsuarioId(actor.usuarioId())
                .orElseThrow(() -> new ForbiddenException("La cuenta no tiene perfil de club"));
        if (!usuarioTrabaja.existsByUsuarioClubIdAndClubId(perfil.getId(), clubId)) {
            throw new ForbiddenException("La cuenta no pertenece a este club");
        }
    }

    private boolean esAdmin(UsuarioAutenticado actor) {
        return actor != null && actor.tieneRol("ADMIN");
    }

    private SolicitudContactoResponseDto toResponse(SolicitudContacto entity) {
        Jugador jugador = entity.getJugador();
        Usuario usuario = jugador == null ? null : jugador.getUsuario();
        return new SolicitudContactoResponseDto(entity.getId(), relations.id(entity.getClub()),
                entity.getClub() == null ? null : entity.getClub().getNombreClub(),
                relations.id(jugador), usuario == null ? null : nombreCompleto(usuario),
                usuario == null ? null : usuario.getFotoPerfil(), relations.id(entity.getUsuarioClub()),
                entity.getAsunto(), entity.getMensaje(), entity.getEstado(), entity.getFechaEnvio(),
                entity.getFechaRespuesta(), entity.getRespuesta());
    }

    private String nombreCompleto(Usuario usuario) {
        return ((usuario.getNombres() == null ? "" : usuario.getNombres()) + " "
                + (usuario.getApellidos() == null ? "" : usuario.getApellidos())).trim();
    }
}

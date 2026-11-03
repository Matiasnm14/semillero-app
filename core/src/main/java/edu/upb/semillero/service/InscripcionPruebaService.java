package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.GestionInscripcionRequestDto;
import edu.upb.semillero.repository.dto.request.InscripcionPruebaRequestDto;
import edu.upb.semillero.repository.dto.response.InscripcionPruebaResponseDto;
import edu.upb.semillero.repository.entity.*;
import edu.upb.semillero.repository.enums.EstadoInscripcionPrueba;
import edu.upb.semillero.repository.enums.EstadoPrueba;
import edu.upb.semillero.repository.repos.*;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.ForbiddenException;
import edu.upb.semillero.service.exception.NotFoundException;
import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InscripcionPruebaService {
    private final InscripcionPruebaRepository repository;
    private final PruebaRepository pruebas;
    private final JugadorRepository jugadores;
    private final UsuarioClubRepository usuarioClubs;
    private final UsuarioTrabajaRepository usuarioTrabaja;
    private final EntityRelations relations;
    private final NotificacionService notificaciones;

    public InscripcionPruebaService(InscripcionPruebaRepository repository, PruebaRepository pruebas,
            JugadorRepository jugadores, UsuarioClubRepository usuarioClubs,
            UsuarioTrabajaRepository usuarioTrabaja, EntityRelations relations,
            NotificacionService notificaciones) {
        this.repository = repository;
        this.pruebas = pruebas;
        this.jugadores = jugadores;
        this.usuarioClubs = usuarioClubs;
        this.usuarioTrabaja = usuarioTrabaja;
        this.relations = relations;
        this.notificaciones = notificaciones;
    }

    @Transactional
    public InscripcionPruebaResponseDto inscribir(Long pruebaId, InscripcionPruebaRequestDto request,
            UsuarioAutenticado actor) {
        if (actor == null || actor.usuarioId() == null) {
            throw new ForbiddenException("Solo un jugador con sesión puede postularse a una prueba");
        }
        Prueba prueba = pruebas.findByIdForUpdate(pruebaId)
                .orElseThrow(() -> new NotFoundException("Prueba con id " + pruebaId + " no existe"));
        if (prueba.getEstado() != EstadoPrueba.ABIERTA) {
            throw new ConflictException("La convocatoria no está abierta");
        }
        Long jugadorId = jugadores.findFirstByUsuarioId(actor.usuarioId()).map(Jugador::getId)
                .orElseThrow(() -> new ForbiddenException("La cuenta no tiene ficha de jugador"));
        if (repository.existsByPruebaIdAndJugadorId(pruebaId, jugadorId)) {
            throw new ConflictException("Ya existe una postulación para esta prueba");
        }
        long activos = repository.countByPruebaIdAndEstadoNot(pruebaId, EstadoInscripcionPrueba.RETIRADO);
        if (prueba.getCupos() != null && activos >= prueba.getCupos()) {
            throw new ConflictException("La convocatoria ya alcanzó su capacidad");
        }
        InscripcionPrueba entity = new InscripcionPrueba();
        entity.setPrueba(prueba);
        entity.setJugador(relations.find(Jugador.class, jugadorId));
        entity.setFechaPostulacion(LocalDateTime.now());
        entity.setEstado(EstadoInscripcionPrueba.PENDIENTE);
        entity.setNota(request == null || request.nota() == null ? null : request.nota().trim());
        entity = repository.saveAndFlush(entity);
        notificarClub(prueba, "NUEVA_POSTULACION", "Nueva postulación a tu convocatoria",
                "Un jugador se postuló a la prueba.", entity.getId());
        return toResponse(entity);
    }

    public List<InscripcionPruebaResponseDto> listar(Long pruebaId, UsuarioAutenticado actor) {
        Prueba prueba = requirePrueba(pruebaId);
        exigirClubPropietarioOAdmin(prueba, actor);
        return repository.findByPruebaIdOrderByFechaPostulacionDesc(pruebaId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public InscripcionPruebaResponseDto gestionar(Long pruebaId, Long inscripcionId,
            GestionInscripcionRequestDto request, UsuarioAutenticado actor) {
        Prueba prueba = requirePrueba(pruebaId);
        exigirClubPropietarioOAdmin(prueba, actor);
        InscripcionPrueba entity = repository.findById(inscripcionId)
                .orElseThrow(() -> new NotFoundException("Postulación con id " + inscripcionId + " no existe"));
        if (!pruebaId.equals(relations.id(entity.getPrueba()))) {
            throw new NotFoundException("La postulación no pertenece a esta convocatoria");
        }
        if (request.estado() != EstadoInscripcionPrueba.ACEPTADO
                && request.estado() != EstadoInscripcionPrueba.RECHAZADO) {
            throw new BusinessException("El club solo puede aceptar o rechazar una postulación");
        }
        if (entity.getEstado() == EstadoInscripcionPrueba.RETIRADO) {
            throw new ConflictException("El jugador ya retiró esta postulación");
        }
        entity.setEstado(request.estado());
        entity.setNotaClub(request.notaClub() == null ? null : request.notaClub().trim());
        entity = repository.saveAndFlush(entity);
        Usuario usuario = entity.getJugador().getUsuario();
        notificaciones.crear(usuario == null ? null : usuario.getId(), "ESTADO_POSTULACION",
                "Actualización de tu postulación", "El club actualizó el estado de tu postulación.",
                entity.getId(), "INSCRIPCION_PRUEBA");
        return toResponse(entity);
    }

    @Transactional
    public void retirar(Long pruebaId, Long inscripcionId, UsuarioAutenticado actor) {
        if (actor == null || actor.usuarioId() == null) {
            throw new ForbiddenException("Debes iniciar sesión como jugador para retirarte");
        }
        Long jugadorId = jugadores.findFirstByUsuarioId(actor.usuarioId()).map(Jugador::getId)
                .orElseThrow(() -> new ForbiddenException("La cuenta no tiene ficha de jugador"));
        InscripcionPrueba entity = repository.findByIdAndJugadorId(inscripcionId, jugadorId)
                .orElseThrow(() -> new NotFoundException("Postulación propia con id " + inscripcionId + " no existe"));
        if (!pruebaId.equals(relations.id(entity.getPrueba()))) {
            throw new NotFoundException("La postulación no pertenece a esta convocatoria");
        }
        if (entity.getEstado() != EstadoInscripcionPrueba.RETIRADO) {
            entity.setEstado(EstadoInscripcionPrueba.RETIRADO);
            repository.saveAndFlush(entity);
        }
    }

    private void notificarClub(Prueba prueba, String tipo, String titulo, String mensaje, Long referencia) {
        UsuarioClub perfil = prueba.getUsuarioClub();
        Usuario usuario = perfil == null ? null : perfil.getUsuario();
        notificaciones.crear(usuario == null ? null : usuario.getId(), tipo, titulo, mensaje,
                referencia, "INSCRIPCION_PRUEBA");
    }

    private Prueba requirePrueba(Long id) {
        return pruebas.findById(id).orElseThrow(() -> new NotFoundException("Prueba con id " + id + " no existe"));
    }

    private void exigirClubPropietarioOAdmin(Prueba prueba, UsuarioAutenticado actor) {
        if (actor != null && actor.tieneRol("ADMIN")) return;
        if (actor == null || actor.clubId() == null || !actor.clubId().equals(relations.id(prueba.getClub()))) {
            throw new ForbiddenException("Solo el club dueño puede consultar postulaciones");
        }
        UsuarioClub perfil = usuarioClubs.findFirstByUsuarioId(actor.usuarioId())
                .orElseThrow(() -> new ForbiddenException("La cuenta no tiene perfil de club"));
        if (!usuarioTrabaja.existsByUsuarioClubIdAndClubId(perfil.getId(), actor.clubId())) {
            throw new ForbiddenException("La cuenta no pertenece al club dueño de la prueba");
        }
    }

    private InscripcionPruebaResponseDto toResponse(InscripcionPrueba entity) {
        Jugador jugador = entity.getJugador();
        Usuario usuario = jugador.getUsuario();
        String nombre = usuario == null ? null : ((usuario.getNombres() == null ? "" : usuario.getNombres()) + " "
                + (usuario.getApellidos() == null ? "" : usuario.getApellidos())).trim();
        return new InscripcionPruebaResponseDto(entity.getId(), relations.id(entity.getPrueba()),
                relations.id(jugador), nombre, usuario == null ? null : usuario.getFotoPerfil(),
                jugador.getPiernaHabil(), entity.getFechaPostulacion(), entity.getEstado(),
                entity.getNota(), entity.getNotaClub());
    }
}

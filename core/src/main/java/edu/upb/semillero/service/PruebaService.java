package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.PruebaRequestDto;
import edu.upb.semillero.repository.dto.response.PruebaResponseDto;
import edu.upb.semillero.repository.entity.*;
import edu.upb.semillero.repository.enums.EstadoInscripcionPrueba;
import edu.upb.semillero.repository.enums.EstadoPrueba;
import edu.upb.semillero.repository.repos.*;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ForbiddenException;
import edu.upb.semillero.service.exception.NotFoundException;
import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PruebaService {
    private final PruebaRepository repository;
    private final EntityRelations relations;
    private final UsuarioClubRepository usuarioClubs;
    private final UsuarioTrabajaRepository usuarioTrabaja;
    private final PosicionRepository posiciones;
    private final InscripcionPruebaRepository inscripciones;

    public PruebaService(PruebaRepository repository, EntityRelations relations,
            UsuarioClubRepository usuarioClubs, UsuarioTrabajaRepository usuarioTrabaja,
            PosicionRepository posiciones, InscripcionPruebaRepository inscripciones) {
        this.repository = repository;
        this.relations = relations;
        this.usuarioClubs = usuarioClubs;
        this.usuarioTrabaja = usuarioTrabaja;
        this.posiciones = posiciones;
        this.inscripciones = inscripciones;
    }

    public Page<PruebaResponseDto> findAll(Pageable pageable, Long clubId) {
        Page<Prueba> page = clubId == null ? repository.findAll(pageable) : repository.findByClubId(clubId, pageable);
        return page.map(this::toResponse);
    }

    public PruebaResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public PruebaResponseDto create(PruebaRequestDto request, UsuarioAutenticado actor) {
        Prueba entity = new Prueba();
        apply(entity, request, actor, true);
        if (entity.getEstado() == null) entity.setEstado(EstadoPrueba.ABIERTA);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public PruebaResponseDto update(Long id, PruebaRequestDto request, UsuarioAutenticado actor) {
        Prueba entity = require(id);
        exigirPropietarioOAdmin(entity, actor);
        apply(entity, request, actor, false);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id, UsuarioAutenticado actor) {
        Prueba entity = require(id);
        exigirPropietarioOAdmin(entity, actor);
        if (inscripciones.countByPruebaId(id) > 0) {
            entity.setEstado(EstadoPrueba.CANCELADA);
            repository.saveAndFlush(entity);
            return;
        }
        repository.delete(entity);
        repository.flush();
    }

    private void apply(Prueba entity, PruebaRequestDto request, UsuarioAutenticado actor, boolean creating) {
        entity.setTitulo(trim(request.titulo()));
        entity.setFechaPrueba(request.fechaPrueba());
        entity.setHoraPrueba(request.horaPrueba());
        entity.setLugar(trim(request.lugar()));
        entity.setCategoria(trim(request.categoria()));
        entity.setCupos(request.cupos());
        entity.setDescripcion(trim(request.descripcion()));
        entity.setRequisitos(trim(request.requisitos()));
        if (request.estado() != null) entity.setEstado(request.estado());
        if (request.posiciones() != null) {
            List<String> names = request.posiciones().stream().filter(s -> s != null && !s.isBlank())
                    .map(String::trim).toList();
            var selected = posiciones.findAll().stream()
                    .filter(p -> names.stream().anyMatch(name -> name.equalsIgnoreCase(p.getPosicion())))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (selected.size() != names.stream().distinct().count()) {
                throw new NotFoundException("Una o más posiciones no existen; consulta /api/v1/posiciones");
            }
            entity.setPosiciones(selected);
        }
        if (esAdmin(actor)) {
            Long clubAntes = relations.id(entity.getClub());
            if (creating || request.clubId() != null) entity.setClub(relations.find(Club.class, request.clubId()));
            if (creating || request.usuarioClubId() != null) {
                entity.setUsuarioClub(relations.find(UsuarioClub.class, request.usuarioClubId()));
            } else if (!creating && request.clubId() != null && !request.clubId().equals(clubAntes)) {
                entity.setUsuarioClub(null);
            }
            if (entity.getClub() == null) throw new BusinessException("ADMIN debe indicar clubId al crear una prueba");
            if (entity.getUsuarioClub() != null
                    && !usuarioTrabaja.existsByUsuarioClubIdAndClubId(
                            entity.getUsuarioClub().getId(), entity.getClub().getId())) {
                throw new ForbiddenException("El perfil indicado no pertenece al club de la prueba");
            }
            return;
        }
        if (actor == null || actor.clubId() == null) {
            throw new ForbiddenException("Solo una cuenta vinculada a un club puede gestionar pruebas");
        }
        UsuarioClub perfil = usuarioClubs.findFirstByUsuarioId(actor.usuarioId())
                .orElseThrow(() -> new ForbiddenException("La cuenta no tiene perfil de club asociado"));
        if (!usuarioTrabaja.existsByUsuarioClubIdAndClubId(perfil.getId(), actor.clubId())) {
            throw new ForbiddenException("La cuenta no pertenece al club indicado por su sesión");
        }
        if (creating) {
            entity.setClub(relations.find(Club.class, actor.clubId()));
            entity.setUsuarioClub(perfil);
        } else if (!actor.clubId().equals(relations.id(entity.getClub()))) {
            throw new ForbiddenException("No puedes modificar pruebas de otro club");
        }
    }

    private void exigirPropietarioOAdmin(Prueba entity, UsuarioAutenticado actor) {
        if (esAdmin(actor)) return;
        if (actor == null || actor.clubId() == null || !actor.clubId().equals(relations.id(entity.getClub()))) {
            throw new ForbiddenException("Solo el club dueño puede gestionar esta prueba");
        }
        UsuarioClub perfil = usuarioClubs.findFirstByUsuarioId(actor.usuarioId())
                .orElseThrow(() -> new ForbiddenException("La cuenta no tiene perfil de club asociado"));
        if (!usuarioTrabaja.existsByUsuarioClubIdAndClubId(perfil.getId(), actor.clubId())) {
            throw new ForbiddenException("La cuenta no pertenece al club dueño de la prueba");
        }
    }

    private boolean esAdmin(UsuarioAutenticado actor) {
        return actor != null && actor.tieneRol("ADMIN");
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private Prueba require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Prueba con id " + id + " no existe"));
    }

    private PruebaResponseDto toResponse(Prueba entity) {
        Club club = entity.getClub();
        String ciudad = club == null || club.getCiudad() == null ? null : club.getCiudad().getNombreCiudad();
        var positionNames = entity.getPosiciones().stream().map(Posicion::getPosicion).sorted().toList();
        long count = inscripciones.countByPruebaIdAndEstadoNot(entity.getId(), EstadoInscripcionPrueba.RETIRADO);
        return new PruebaResponseDto(entity.getId(), entity.getTitulo(), entity.getFechaPrueba(),
                entity.getHoraPrueba(), relations.id(club), relations.id(entity.getUsuarioClub()),
                club == null ? null : club.getNombreClub(), ciudad, club == null ? null : club.getFotoClub(),
                entity.getLugar(), entity.getCategoria(), positionNames, entity.getCupos(), entity.getCupos(),
                count, entity.getDescripcion(), entity.getRequisitos(), entity.getEstado());
    }
}

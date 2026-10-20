package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.ParticipacionPartidoRequestDto;
import edu.upb.semillero.repository.dto.response.ParticipacionPartidoResponseDto;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.ParticipacionPartido;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.repos.ParticipacionPartidoRepository;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ParticipacionPartidoService {
    private final ParticipacionPartidoRepository repository;
    private final EntityRelations relations;

    public ParticipacionPartidoService(ParticipacionPartidoRepository repository, EntityRelations relations) {
        this.repository = repository;
        this.relations = relations;
    }

    public Page<ParticipacionPartidoResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public ParticipacionPartidoResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public ParticipacionPartidoResponseDto create(ParticipacionPartidoRequestDto request) {
        ParticipacionPartido entity = new ParticipacionPartido();
        apply(entity, request);
        validate(entity);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public ParticipacionPartidoResponseDto update(Long id, ParticipacionPartidoRequestDto request) {
        ParticipacionPartido entity = require(id);
        apply(entity, request);
        validate(entity);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private ParticipacionPartido require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("ParticipacionPartido con id " + id + " no existe"));
    }

    private void apply(ParticipacionPartido entity, ParticipacionPartidoRequestDto request) {
        entity.setPartido(relations.find(Partido.class, request.partidoId()));
        entity.setJugador(relations.find(Jugador.class, request.jugadorId()));
        entity.setClub(relations.find(Club.class, request.clubId()));
        entity.setEsTitular(request.esTitular());
        entity.setDorsal(request.dorsal());
        entity.setMinutoEntrada(request.minutoEntrada() == null ? (short) 0 : request.minutoEntrada());
        entity.setMinutoSalida(request.minutoSalida());
        entity.setVelocidadProm(request.velocidadProm());
        entity.setDistanciaRecorrida(request.distanciaRecorrida());
    }

    private ParticipacionPartidoResponseDto toResponse(ParticipacionPartido entity) {
        return new ParticipacionPartidoResponseDto(
                entity.getId(),
                relations.id(entity.getPartido()),
                relations.id(entity.getJugador()),
                relations.id(entity.getClub()),
                entity.getEsTitular(),
                entity.getDorsal(),
                entity.getMinutoEntrada(),
                entity.getMinutoSalida(),
                entity.getVelocidadProm(),
                entity.getDistanciaRecorrida());
    }

    private void validate(ParticipacionPartido entity) {
        Partido partido = entity.getPartido();
        Long clubId = entity.getClub().getId();
        if (!clubId.equals(partido.getClubLocal().getId()) && !clubId.equals(partido.getClubVisitante().getId())) {
            throw new BusinessException("El club debe ser local o visitante del partido");
        }
        if (entity.getMinutoSalida() != null && entity.getMinutoSalida() < entity.getMinutoEntrada()) {
            throw new BusinessException("El minuto de salida no puede ser anterior al de entrada");
        }
        repository.findByPartidoIdAndJugadorId(
                partido.getId(), entity.getJugador().getId())
                .filter(existing -> !Objects.equals(existing.getId(), entity.getId()))
                .ifPresent(existing -> {
                    throw new ConflictException("El jugador ya participa en este partido");
                });
    }
}

package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.JugadorRequestDto;
import edu.upb.semillero.repository.dto.response.JugadorResponseDto;
import edu.upb.semillero.repository.dto.response.JugadorDirectorioResponseDto;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.repos.FichadoRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.PosicionJugadorRepository;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JugadorService {
    private final JugadorRepository repository;
    private final EntityRelations relations;
    private final PosicionJugadorRepository posiciones;
    private final FichadoRepository fichados;

    public JugadorService(JugadorRepository repository, EntityRelations relations,
            PosicionJugadorRepository posiciones, FichadoRepository fichados) {
        this.repository = repository;
        this.relations = relations;
        this.posiciones = posiciones;
        this.fichados = fichados;
    }

    public Page<JugadorDirectorioResponseDto> findAll(Pageable pageable, String posicion,
            String piernaHabil, Integer edadMin, Integer edadMax, String nombre) {
        if (edadMin != null && edadMax != null && edadMin > edadMax) {
            throw new BusinessException("edadMin no puede ser mayor que edadMax");
        }
        Page<Jugador> page = repository.findAll(JugadorSpecifications.filtrar(posicion, piernaHabil,
                edadMin, edadMax, nombre), pageable);
        var ids = page.getContent().stream().map(Jugador::getId).toList();
        Map<Long, java.util.List<String>> namesByPlayer = ids.isEmpty() ? Map.of()
                : posiciones.findByJugadorIdIn(ids).stream().filter(link -> link.getPosicion() != null)
                        .collect(Collectors.groupingBy(link -> link.getJugador().getId(),
                                Collectors.mapping(link -> link.getPosicion().getPosicion(),
                                        Collectors.collectingAndThen(Collectors.toList(), names -> names.stream().distinct().toList()))));
        Map<Long, edu.upb.semillero.repository.entity.Fichado> currentByPlayer = ids.isEmpty() ? Map.of()
                : fichados.findVigentesByJugadorIdIn(ids, LocalDateTime.now()).stream()
                        .collect(Collectors.toMap(f -> f.getJugador().getId(), f -> f, (first, ignored) -> first));
        return page.map(entity -> toDirectoryResponse(entity, namesByPlayer, currentByPlayer));
    }

    public JugadorResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public JugadorResponseDto create(JugadorRequestDto request) {
        Jugador entity = new Jugador();
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public JugadorResponseDto update(Long id, JugadorRequestDto request) {
        Jugador entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Jugador require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Jugador con id " + id + " no existe"));
    }

    private void apply(Jugador entity, JugadorRequestDto request) {
        entity.setPiernaHabil(request.piernaHabil());
        entity.setFechaNacimiento(request.fechaNacimiento());
        entity.setAltura(request.altura());
        entity.setPeso(request.peso());
        entity.setDescripcionJugador(request.descripcionJugador());
        entity.setUsuario(relations.find(Usuario.class, request.usuarioId()));
    }

    private JugadorResponseDto toResponse(Jugador entity) {
        return new JugadorResponseDto(
                entity.getId(),
                entity.getPiernaHabil(),
                entity.getFechaNacimiento(),
                entity.getAltura(),
                entity.getPeso(),
                entity.getDescripcionJugador(),
                relations.id(entity.getUsuario()));
    }

    private JugadorDirectorioResponseDto toDirectoryResponse(Jugador entity,
            Map<Long, java.util.List<String>> namesByPlayer,
            Map<Long, edu.upb.semillero.repository.entity.Fichado> currentByPlayer) {
        Usuario usuario = entity.getUsuario();
        var posicionNames = namesByPlayer.getOrDefault(entity.getId(), java.util.List.of());
        var clubActual = java.util.Optional.ofNullable(currentByPlayer.get(entity.getId()))
                .map(f -> new JugadorDirectorioResponseDto.ClubActual(
                        relations.id(f.getClub()), f.getClub().getNombreClub())).orElse(null);
        return new JugadorDirectorioResponseDto(entity.getId(), entity.getPiernaHabil(),
                entity.getFechaNacimiento(), entity.getAltura(), entity.getPeso(),
                entity.getDescripcionJugador(), relations.id(usuario),
                usuario == null ? null : usuario.getNombres(),
                usuario == null ? null : usuario.getApellidos(),
                usuario == null ? null : usuario.getFotoPerfil(), posicionNames, clubActual);
    }
}

package edu.upb.semillero.service.comun;

import edu.upb.semillero.repository.entity.UsuarioTrabaja;
import edu.upb.semillero.repository.repos.AdministradorRepository;
import edu.upb.semillero.repository.repos.ClubRepository;
import edu.upb.semillero.repository.repos.UsuarioRepository;
import edu.upb.semillero.repository.repos.UsuarioRolRepository;
import edu.upb.semillero.repository.repos.UsuarioTrabajaRepository;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La implementación de {@link ComunService}, tarea S2-02. El punto 11 del
 * acuerdo de la S1-04 se la asignaba a ficha técnica, que no la entregó; la HU
 * 16 no se puede cerrar sin esto, así que la escribió torneos.
 *
 * <p>Los roles se arman con las tres fuentes del punto 21 del acuerdo:
 *
 * <ul>
 *   <li>ADMIN: la tabla {@code administrador}.</li>
 *   <li>ENTRENADOR: {@code usuario_trabaja.tipo_usuario_club = 'EN'}.</li>
 *   <li>ORGANIZADOR, MESA_CONTROL y CONSULTA: {@code roles} y
 *       {@code usuarios_roles} (migración V008).</li>
 * </ul>
 *
 * <p>Un rol asignado en {@code usuarios_roles} vale igual para ADMIN y
 * ENTRENADOR, así se puede dar de alta a alguien a mano sin tocar las tablas
 * del otro módulo.
 */
@Service
@Transactional(readOnly = true)
public class ComunServiceImpl implements ComunService {
    /** Código de entrenador en usuario_trabaja (punto 15 del acuerdo). */
    private static final String TIPO_ENTRENADOR = "EN";

    private final UsuarioRepository usuarios;
    private final ClubRepository clubes;
    private final AdministradorRepository administradores;
    private final UsuarioTrabajaRepository usuariosTrabaja;
    private final UsuarioRolRepository usuariosRoles;

    public ComunServiceImpl(UsuarioRepository usuarios, ClubRepository clubes,
            AdministradorRepository administradores, UsuarioTrabajaRepository usuariosTrabaja,
            UsuarioRolRepository usuariosRoles) {
        this.usuarios = usuarios;
        this.clubes = clubes;
        this.administradores = administradores;
        this.usuariosTrabaja = usuariosTrabaja;
        this.usuariosRoles = usuariosRoles;
    }

    @Override
    public boolean usuarioExiste(Long usuarioId) {
        return usuarioId != null && usuarios.existsById(usuarioId);
    }

    @Override
    public boolean clubExiste(Long clubId) {
        return clubId != null && clubes.existsById(clubId);
    }

    @Override
    public boolean tieneRol(Long usuarioId, String codigoRol) {
        if (usuarioId == null || codigoRol == null) {
            return false;
        }
        if (usuariosRoles.existsByUsuarioIdAndRolCodigo(usuarioId, codigoRol)) {
            return true;
        }
        return switch (codigoRol) {
            case Roles.ADMIN -> administradores.findFirstByUsuarioId(usuarioId).isPresent();
            case Roles.ENTRENADOR -> esEntrenador(usuarioId);
            default -> false;
        };
    }

    @Override
    public Set<Long> clubesDondeTrabaja(Long usuarioId) {
        if (usuarioId == null) {
            return Set.of();
        }
        return usuariosTrabaja.findByUsuarioClubUsuarioId(usuarioId).stream()
                .map(UsuarioTrabaja::getClub)
                .filter(Objects::nonNull)
                .map(club -> club.getId())
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> rolesDe(Long usuarioId) {
        Set<String> roles = usuariosRoles.findByUsuarioId(usuarioId).stream()
                .map(usuarioRol -> usuarioRol.getRol().getCodigo())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (administradores.findFirstByUsuarioId(usuarioId).isPresent()) {
            roles.add(Roles.ADMIN);
        }
        if (esEntrenador(usuarioId)) {
            roles.add(Roles.ENTRENADOR);
        }
        if (roles.isEmpty()) {
            roles.add(Roles.CONSULTA);
        }
        return roles;
    }

    private boolean esEntrenador(Long usuarioId) {
        return usuariosTrabaja.findByUsuarioClubUsuarioId(usuarioId).stream()
                .anyMatch(vinculo -> TIPO_ENTRENADOR.equalsIgnoreCase(
                        vinculo.getTipoUsuarioClub() == null
                                ? null : vinculo.getTipoUsuarioClub().trim()));
    }
}

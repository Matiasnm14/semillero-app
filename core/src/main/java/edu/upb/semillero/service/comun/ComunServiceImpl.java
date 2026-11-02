package edu.upb.semillero.service.comun;

import edu.upb.semillero.repository.entity.UsuarioTrabaja;
import edu.upb.semillero.repository.repos.AdministradorRepository;
import edu.upb.semillero.repository.repos.ClubRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.UsuarioClubRepository;
import edu.upb.semillero.repository.repos.UsuarioRepository;
import edu.upb.semillero.repository.repos.UsuarioRolRepository;
import edu.upb.semillero.repository.repos.UsuarioTrabajaRepository;
import java.util.LinkedHashSet;
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
 * <p>Los roles están unificados con el resto de los módulos (ver {@link Roles}):
 * son las cuatro categorías del login común, derivadas de las tablas que ya
 * existen.
 *
 * <ul>
 *   <li>{@code ADMINISTRADOR_GENERAL}: la tabla {@code administrador}.</li>
 *   <li>{@code JUGADOR}: la tabla {@code jugadores}.</li>
 *   <li>{@code USUARIO_CLUB}: la tabla {@code usuarios_club}.</li>
 *   <li>{@code USUARIO_SISTEMA}: una fila en {@code usuarios_roles}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ComunServiceImpl implements ComunService {
    private final UsuarioRepository usuarios;
    private final ClubRepository clubes;
    private final AdministradorRepository administradores;
    private final JugadorRepository jugadores;
    private final UsuarioClubRepository usuariosClub;
    private final UsuarioTrabajaRepository usuariosTrabaja;
    private final UsuarioRolRepository usuariosRoles;

    public ComunServiceImpl(UsuarioRepository usuarios, ClubRepository clubes,
            AdministradorRepository administradores, JugadorRepository jugadores,
            UsuarioClubRepository usuariosClub, UsuarioTrabajaRepository usuariosTrabaja,
            UsuarioRolRepository usuariosRoles) {
        this.usuarios = usuarios;
        this.clubes = clubes;
        this.administradores = administradores;
        this.jugadores = jugadores;
        this.usuariosClub = usuariosClub;
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
        return switch (codigoRol) {
            case Roles.ADMINISTRADOR_GENERAL -> administradores.findFirstByUsuarioId(usuarioId).isPresent();
            case Roles.JUGADOR -> jugadores.findFirstByUsuarioId(usuarioId).isPresent();
            case Roles.USUARIO_CLUB -> usuariosClub.findFirstByUsuarioId(usuarioId).isPresent();
            case Roles.USUARIO_SISTEMA -> !usuariosRoles.findByUsuarioId(usuarioId).isEmpty();
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
        Set<String> roles = new LinkedHashSet<>();
        if (usuarioId == null) {
            return roles;
        }
        if (administradores.findFirstByUsuarioId(usuarioId).isPresent()) {
            roles.add(Roles.ADMINISTRADOR_GENERAL);
        }
        if (jugadores.findFirstByUsuarioId(usuarioId).isPresent()) {
            roles.add(Roles.JUGADOR);
        }
        if (usuariosClub.findFirstByUsuarioId(usuarioId).isPresent()) {
            roles.add(Roles.USUARIO_CLUB);
        }
        if (!usuariosRoles.findByUsuarioId(usuarioId).isEmpty()) {
            roles.add(Roles.USUARIO_SISTEMA);
        }
        return roles;
    }
}

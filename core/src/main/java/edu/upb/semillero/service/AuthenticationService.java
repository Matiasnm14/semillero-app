package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.LoginRequestDto;
import edu.upb.semillero.repository.dto.response.LoginResponseDto;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.UsuarioTrabaja;
import edu.upb.semillero.repository.repos.AdministradorRepository;
import edu.upb.semillero.repository.repos.FichadoRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.UsuarioClubRepository;
import edu.upb.semillero.repository.repos.UsuarioRepository;
import edu.upb.semillero.repository.repos.UsuarioRolRepository;
import edu.upb.semillero.repository.repos.UsuarioTrabajaRepository;
import edu.upb.semillero.service.comun.ComunService;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.HighlightAccessDeniedException;
import edu.upb.semillero.service.seguridad.JwtService;
import java.util.List;

import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@Transactional(readOnly = true)
public class AuthenticationService {
    private final UsuarioRepository usuarios;
    private final AdministradorRepository administradores;
    private final JugadorRepository jugadores;
    private final FichadoRepository fichados;
    private final UsuarioClubRepository usuariosClub;
    private final UsuarioTrabajaRepository usuariosTrabaja;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioRolRepository usuariosRoles;
    private final ComunService comun;
    private final JwtService jwt;

    public AuthenticationService(
            UsuarioRepository usuarios,
            AdministradorRepository administradores,
            JugadorRepository jugadores,
            FichadoRepository fichados,
            UsuarioClubRepository usuariosClub,
            UsuarioTrabajaRepository usuariosTrabaja,
            UsuarioRolRepository usuariosRoles,
            PasswordEncoder passwordEncoder,
            ComunService comun,
            JwtService jwt) {
        this.usuarios = usuarios;
        this.administradores = administradores;
        this.jugadores = jugadores;
        this.fichados = fichados;
        this.usuariosClub = usuariosClub;
        this.usuariosTrabaja = usuariosTrabaja;
        this.usuariosRoles = usuariosRoles;
        this.passwordEncoder = passwordEncoder;
        this.comun = comun;
        this.jwt = jwt;
    }

    @Transactional
    public LoginResponseDto login(LoginRequestDto request) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(request.identificador().trim())
                .or(() -> usuarios.findByNameIgnoreCase(request.identificador().trim()))
                .orElseThrow(() -> new BusinessException("Credenciales inválidas"));
        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new BusinessException("Credenciales inválidas");
        }
        if (administradores.findFirstByUsuarioId(usuario.getId()).isPresent()) {
            return response(usuario, "ADMINISTRADOR_GENERAL", null, null, null);
        }
        var jugador = jugadores.findFirstByUsuarioId(usuario.getId());
        if (jugador.isPresent()) {
            return response(usuario, "JUGADOR", jugador.get().getId(), null, null);
        }
        var usuarioClub = usuariosClub.findFirstByUsuarioId(usuario.getId());
        if (usuarioClub.isPresent()) {
            UsuarioTrabaja vinculacion = usuariosTrabaja
                    .findFirstByUsuarioClubId(usuarioClub.get().getId())
                    .orElseThrow(() -> new BusinessException("El usuario del club no tiene un club asignado"));
            return response(usuario, "USUARIO_CLUB", null, vinculacion.getClub().getId(),
                    vinculacion.getClub().getNombreClub());
        }
        // Tarea S2-01: una cuenta del sistema (organizador, mesa de control o
        // consulta) no está en administrador, jugadores ni usuarios_club: su rol
        // sale de usuarios_roles (migración V008).
        if (!usuariosRoles.findByUsuarioId(usuario.getId()).isEmpty()) {
            return response(usuario, "USUARIO_SISTEMA", null, null, null);
        }
        throw new BusinessException("La cuenta no tiene un rol asignado");
    }

    /**
     * Tarea S2-01: la respuesta lleva además el JWT y los roles de la HU 16,
     * que son los que mira la autorización de los endpoints de torneos.
     */
    private LoginResponseDto response(Usuario usuario, String rol, Long jugadorId, Long clubId, String nombreClub) {
        List<String> roles = List.copyOf(comun.rolesDe(usuario.getId()));
        return new LoginResponseDto(usuario.getId(), usuario.getName(), usuario.getNombres(),
                usuario.getApellidos(), usuario.getEmail(), rol, jugadorId, clubId, nombreClub,
                usuario.getFotoPerfil(),
                jwt.emitir(usuario.getId(), usuario.getName(), roles, clubId),
                roles);
    }

    /**
     * Authorizes an operation over a player's highlights. Public reads do not
     * enter this method; private history and every mutation do.
     */
    private UsuarioAutenticado requireJwt(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new HighlightAccessDeniedException(
                    "Debes iniciar sesión para administrar highlights");
        }

        return jwt.validar(authorization.substring("Bearer ".length()).trim())
                .orElseThrow(() -> new HighlightAccessDeniedException(
                        "La sesión no es válida"));
    }
    public void authorizeHighlightManagement(String authorization, Long jugadorId) {
        var autenticado = requireJwt(authorization);

        if (administradores.findFirstByUsuarioId(autenticado.usuarioId()).isPresent()) {
            return;
        }

        var jugador = jugadores.findFirstByUsuarioId(autenticado.usuarioId());
        if (jugador.isPresent() && jugadorId.equals(jugador.get().getId())) {
            return;
        }

        boolean esUsuarioClub =
                usuariosClub.findFirstByUsuarioId(autenticado.usuarioId()).isPresent();

        if (esUsuarioClub && autenticado.clubId() != null
                && fichados.existsByJugadorIdAndClubIdAndFechaFinalFichajeIsNull(
                jugadorId, autenticado.clubId())) {
            return;
        }

        throw new HighlightAccessDeniedException(
                "No tienes permiso para administrar los highlights de este jugador");
    }

    /** Hidden history is private to the player, even from their club. */
    public void authorizeHiddenHighlightHistory(String authorization, Long jugadorId) {
        var autenticado = requireJwt(authorization);

        var jugador = jugadores.findFirstByUsuarioId(autenticado.usuarioId());
        if (jugador.isPresent() && jugadorId.equals(jugador.get().getId())) {
            return;
        }

        throw new HighlightAccessDeniedException(
                "Solo el jugador propietario puede consultar sus highlights ocultos");
    }
}

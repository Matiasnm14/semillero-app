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
import edu.upb.semillero.repository.repos.UsuarioTrabajaRepository;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.HighlightAccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional(readOnly = true)
public class AuthenticationService {
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    private final UsuarioRepository usuarios;
    private final AdministradorRepository administradores;
    private final JugadorRepository jugadores;
    private final FichadoRepository fichados;
    private final UsuarioClubRepository usuariosClub;
    private final UsuarioTrabajaRepository usuariosTrabaja;
    private final PasswordEncoder passwordEncoder;
    private final Map<String, SessionScope> sessions = new ConcurrentHashMap<>();

    public AuthenticationService(
            UsuarioRepository usuarios,
            AdministradorRepository administradores,
            JugadorRepository jugadores,
            FichadoRepository fichados,
            UsuarioClubRepository usuariosClub,
            UsuarioTrabajaRepository usuariosTrabaja,
            PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.administradores = administradores;
        this.jugadores = jugadores;
        this.fichados = fichados;
        this.usuariosClub = usuariosClub;
        this.usuariosTrabaja = usuariosTrabaja;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public LoginResponseDto login(LoginRequestDto request) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(request.identificador().trim())
                .or(() -> usuarios.findByNameIgnoreCase(request.identificador().trim()))
                .orElseThrow(() -> new BusinessException("Credenciales inválidas"));
        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new BusinessException("Credenciales inválidas");
        }
        SessionScope scope = scopeFor(usuario);
        String token = newAccessToken();
        sessions.put(token, scope);
        return response(usuario, scope.role(), scope.playerId(), scope.clubId(), scope.clubName(), token);
    }

    private LoginResponseDto response(
            Usuario usuario, String rol, Long jugadorId, Long clubId, String nombreClub, String accessToken) {
        return new LoginResponseDto(usuario.getId(), usuario.getName(), usuario.getNombres(),
                usuario.getApellidos(), usuario.getEmail(), rol, jugadorId, clubId, nombreClub,
                usuario.getFotoPerfil(), accessToken);
    }

    /**
     * Authorizes an operation over a player's highlights. Public reads do not
     * enter this method; private history and every mutation do.
     */
    public void authorizeHighlightManagement(String authorization, Long jugadorId) {
        SessionScope scope = requireSession(authorization);
        if ("ADMINISTRADOR_GENERAL".equals(scope.role())) return;
        if ("JUGADOR".equals(scope.role()) && jugadorId.equals(scope.playerId())) return;
        if ("USUARIO_CLUB".equals(scope.role()) && scope.clubId() != null
                && fichados.existsByJugadorIdAndClubIdAndFechaFinalFichajeIsNull(jugadorId, scope.clubId())) return;
        throw new HighlightAccessDeniedException(
                "No tienes permiso para administrar los highlights de este jugador");
    }

    /** Hidden history is private to the player, even from their club. */
    public void authorizeHiddenHighlightHistory(String authorization, Long jugadorId) {
        SessionScope scope = requireSession(authorization);
        if ("JUGADOR".equals(scope.role()) && jugadorId.equals(scope.playerId())) return;
        throw new HighlightAccessDeniedException(
                "Solo el jugador propietario puede consultar sus highlights ocultos");
    }

    private SessionScope requireSession(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new HighlightAccessDeniedException(
                    "Debes iniciar sesión para administrar highlights");
        }
        SessionScope scope = sessions.get(authorization.substring("Bearer ".length()).trim());
        if (scope == null) {
            throw new HighlightAccessDeniedException("La sesión no es válida");
        }
        return scope;
    }

    private String newAccessToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private SessionScope scopeFor(Usuario usuario) {
        if (administradores.findFirstByUsuarioId(usuario.getId()).isPresent()) {
            return new SessionScope("ADMINISTRADOR_GENERAL", null, null, null);
        }
        var jugador = jugadores.findFirstByUsuarioId(usuario.getId());
        if (jugador.isPresent()) {
            var fichadoActual = fichados
                    .findFirstByJugadorIdAndFechaFinalFichajeIsNullOrderByFechaFichajeDesc(jugador.get().getId());
            return new SessionScope("JUGADOR", jugador.get().getId(),
                    fichadoActual.map(fichado -> fichado.getClub().getId()).orElse(null),
                    fichadoActual.map(fichado -> fichado.getClub().getNombreClub()).orElse(null));
        }
        var usuarioClub = usuariosClub.findFirstByUsuarioId(usuario.getId())
                .orElseThrow(() -> new BusinessException("La cuenta no tiene un rol asignado"));
        UsuarioTrabaja vinculacion = usuariosTrabaja.findFirstByUsuarioClubId(usuarioClub.getId())
                .orElseThrow(() -> new BusinessException("El usuario del club no tiene un club asignado"));
        return new SessionScope("USUARIO_CLUB", null, vinculacion.getClub().getId(),
                vinculacion.getClub().getNombreClub());
    }

    private record SessionScope(String role, Long playerId, Long clubId, String clubName) {}
}

package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.LoginRequestDto;
import edu.upb.semillero.repository.dto.response.LoginResponseDto;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.UsuarioTrabaja;
import edu.upb.semillero.repository.repos.AdministradorRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.UsuarioClubRepository;
import edu.upb.semillero.repository.repos.UsuarioRepository;
import edu.upb.semillero.repository.repos.UsuarioTrabajaRepository;
import edu.upb.semillero.service.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthenticationService {
    private final UsuarioRepository usuarios;
    private final AdministradorRepository administradores;
    private final JugadorRepository jugadores;
    private final UsuarioClubRepository usuariosClub;
    private final UsuarioTrabajaRepository usuariosTrabaja;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(
            UsuarioRepository usuarios,
            AdministradorRepository administradores,
            JugadorRepository jugadores,
            UsuarioClubRepository usuariosClub,
            UsuarioTrabajaRepository usuariosTrabaja,
            PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.administradores = administradores;
        this.jugadores = jugadores;
        this.usuariosClub = usuariosClub;
        this.usuariosTrabaja = usuariosTrabaja;
        this.passwordEncoder = passwordEncoder;
    }

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
        throw new BusinessException("La cuenta no tiene un rol asignado");
    }

    private LoginResponseDto response(
            Usuario usuario, String rol, Long jugadorId, Long clubId, String nombreClub) {
        return new LoginResponseDto(usuario.getId(), usuario.getName(), usuario.getNombres(),
                usuario.getApellidos(), usuario.getEmail(), rol, jugadorId, clubId, nombreClub,
                usuario.getFotoPerfil());
    }
}

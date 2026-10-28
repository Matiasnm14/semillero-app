package edu.upb.semillero.repository.dto.response;

import java.util.List;

/**
 * La respuesta del login común.
 *
 * <p>Tarea S2-01: se le agregaron {@code token} y {@code roles}. {@code rol} es
 * el que ya devolvía (ADMINISTRADOR_GENERAL, JUGADOR o USUARIO_CLUB) y queda
 * como estaba; {@code roles} son los códigos de la HU 16 (ADMIN, ORGANIZADOR,
 * ENTRENADOR, MESA_CONTROL, CONSULTA), los mismos que viajan en el token y los
 * que mira la autorización de los endpoints de torneos.
 */
public record LoginResponseDto(
        Long id,
        String name,
        String nombres,
        String apellidos,
        String email,
        String rol,
        Long jugadorId,
        Long clubId,
        String nombreClub,
        String fotoPerfil,
        String token,
        List<String> roles) {}

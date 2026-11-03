package edu.upb.semillero.repository.dto.response;

import edu.upb.semillero.repository.enums.EstadoSolicitudContacto;
import java.time.LocalDateTime;

public record SolicitudContactoResponseDto(
        Long id, Long clubId, String nombreClub, Long jugadorId, String nombreJugador,
        String fotoJugador, Long usuarioClubId, String asunto, String mensaje,
        EstadoSolicitudContacto estado, LocalDateTime fechaEnvio,
        LocalDateTime fechaRespuesta, String respuesta
) {}

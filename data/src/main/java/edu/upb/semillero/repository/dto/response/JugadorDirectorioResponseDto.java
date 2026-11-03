package edu.upb.semillero.repository.dto.response;

import java.time.LocalDate;
import java.util.List;

public record JugadorDirectorioResponseDto(Long id, String piernaHabil, LocalDate fechaNacimiento,
        Double altura, Double peso, String descripcionJugador, Long usuarioId,
        String nombres, String apellidos, String fotoPerfil, List<String> posiciones,
        ClubActual clubActual) {
    public record ClubActual(Long id, String nombreClub) {}
}

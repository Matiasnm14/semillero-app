package edu.upb.semillero.repository.dto.response.scouting;

import java.time.Instant;
import java.util.UUID;

public record InscripcionPruebaVista(
        UUID id,
        Long jugadorId,
        String nombreJugador,
        String fotoJugador,
        String piernaHabil,
        Integer edad,
        String estado,
        String nota,
        String asistencia,
        String observaciones,
        Instant fechaPostulacion,
        Instant evaluadaEn,
        Long version) {}

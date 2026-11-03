package edu.upb.semillero.repository.dto.response.scouting;

import java.time.*;
import java.util.Set;

public record ConvocatoriaPruebaVista(
        Long id,
        Long clubId,
        String nombreClub,
        String ciudadClub,
        String fotoClub,
        String titulo,
        LocalDate fechaPrueba,
        LocalTime horaPrueba,
        String zonaHoraria,
        Instant inicio,
        String lugar,
        String categoria,
        String descripcion,
        int cupos,
        long inscritos,
        Instant limiteInscripcion,
        Integer edadMin,
        Integer edadMax,
        Set<String> posiciones,
        Set<String> requisitos,
        String estado,
        Long version,
        boolean puedeGestionar,
        InscripcionPruebaVista inscripcionPropia) {}

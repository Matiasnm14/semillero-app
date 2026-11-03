package edu.upb.semillero.repository.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import edu.upb.semillero.repository.enums.EstadoPrueba;

public record PruebaResponseDto(
        Long id,
        String titulo,
        LocalDate fechaPrueba,
        LocalTime horaPrueba,
        Long clubId,
        Long usuarioClubId,
        String nombreClub,
        String ciudadClub,
        String logoClub,
        String lugar,
        String categoria,
        List<String> posiciones,
        Integer cupos,
        Integer cupo,
        long inscritosCount,
        String descripcion,
        String requisitos,
        EstadoPrueba estado
) {}

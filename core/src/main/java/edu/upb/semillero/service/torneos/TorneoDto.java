package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.enums.torneos.AmbitoTorneo;
import edu.upb.semillero.repository.enums.torneos.EstadoTorneo;
import java.time.LocalDate;

public record TorneoDto(
        Long torneoId,
        String nombre,
        Short temporada,
        AmbitoTorneo ambito,
        EstadoTorneo estado,
        LocalDate fechaInicio,
        LocalDate fechaFin
) {}

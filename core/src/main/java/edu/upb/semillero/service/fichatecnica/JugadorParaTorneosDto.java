package edu.upb.semillero.service.fichatecnica;

import edu.upb.semillero.repository.enums.EstadoJugador;
import java.util.Set;

/**
 * Los hechos que torneos necesita de un jugador para validar la lista de buena
 * fe (HU 5). Sin datos personales: la edad viene calculada a la fecha de corte,
 * as&iacute; torneos no maneja fechas de nacimiento ni nombres.
 */
public record JugadorParaTorneosDto(
        Long jugadorId,
        int edadAFechaDeCorte,
        EstadoJugador estado,
        boolean documentosVigentes,
        boolean aptoMedicoVigente,
        Set<Long> clubesDondeEstaFichado
) {}

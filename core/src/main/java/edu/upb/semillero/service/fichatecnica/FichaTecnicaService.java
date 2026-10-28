package edu.upb.semillero.service.fichatecnica;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Contrato que ficha t&eacute;cnica le ofrece a los dem&aacute;s m&oacute;dulos. Punto 10 del
 * acuerdo de la S1-04: la escribe torneos, la implementa ficha t&eacute;cnica.
 *
 * <p>Dos criterios acordados: solo IDs, nunca datos personales; y este
 * m&oacute;dulo da hechos, no juicios. Las reglas de elegibilidad de la lista de
 * buena fe son de torneos, que las aplica sobre estos datos y devuelve el
 * motivo exacto del rechazo.
 *
 * <p>Mientras ficha t&eacute;cnica no la implemente, el bean que responde es el stub
 * de {@code service.torneos.MocksExternosConfiguration}.
 */
public interface FichaTecnicaService {

    /** Devuelve vac&iacute;o si el jugador no existe. */
    Optional<JugadorParaTorneosDto> datosParaTorneos(Long jugadorId, LocalDate fechaCorte);

    /** Jugadores fichados en un club a una fecha. Solo IDs. */
    List<Long> jugadoresFichadosEn(Long clubId, LocalDate fecha);
}

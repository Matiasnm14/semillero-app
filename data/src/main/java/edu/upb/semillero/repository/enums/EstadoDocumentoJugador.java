package edu.upb.semillero.repository.enums;

/**
 * Estado de un documento del jugador. Solo un documento {@code VIGENTE} (y no
 * vencido) habilita al jugador en la lista de buena fe (HU 5).
 */
public enum EstadoDocumentoJugador {
    PENDIENTE, VIGENTE, VENCIDO, RECHAZADO
}

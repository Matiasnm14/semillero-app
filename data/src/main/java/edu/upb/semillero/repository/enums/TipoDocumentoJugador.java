package edu.upb.semillero.repository.enums;

/**
 * Tipos de documento de un jugador. La mantiene ficha técnica; torneos solo la
 * lee para validar la lista de buena fe (HU 5).
 */
public enum TipoDocumentoJugador {
    CI, CERTIFICADO_NACIMIENTO, APTO_MEDICO, AUTORIZACION_PADRES, SEGURO
}

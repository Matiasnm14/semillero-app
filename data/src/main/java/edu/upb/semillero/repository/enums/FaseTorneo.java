package edu.upb.semillero.repository.enums;

/**
 * Fase del partido dentro de una categoría del torneo. Vive junto a
 * EstadoPartido, en los enums compartidos, porque la referencia la entidad
 * compartida Partido.
 */
public enum FaseTorneo {
    LIGA, GRUPOS, OCTAVOS, CUARTOS, SEMIFINAL, TERCER_PUESTO, FINAL
}

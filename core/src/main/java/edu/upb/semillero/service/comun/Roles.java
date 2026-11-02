package edu.upb.semillero.service.comun;

/**
 * Los cinco roles de la HU 16, en un solo lugar para que el código no los
 * escriba a mano. Los mismos códigos están en la tabla {@code roles} (migración
 * V008) y son los que viajan en el token.
 */
public final class Roles {
    public static final String ADMIN = "ADMIN";
    public static final String ORGANIZADOR = "ORGANIZADOR";
    public static final String ENTRENADOR = "ENTRENADOR";
    public static final String MESA_CONTROL = "MESA_CONTROL";
    public static final String CONSULTA = "CONSULTA";
    public static final String SCOUT = "SCOUT";
    public static final String COORDINADOR_SCOUTING = "COORDINADOR_SCOUTING";
    public static final String RECLUTADOR = "RECLUTADOR";
    public static final String EVALUADOR = "EVALUADOR";
    public static final String RESPONSABLE_SALVAGUARDA = "RESPONSABLE_SALVAGUARDA";
    public static final String SCOUTING_AUDITOR = "SCOUTING_AUDITOR";
    public static final String SCOUTING_CONFIG = "SCOUTING_CONFIG";
    public static final String SCOUTING_STATUS = "SCOUTING_STATUS";

    private Roles() {
    }
}

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

    private Roles() {
    }
}

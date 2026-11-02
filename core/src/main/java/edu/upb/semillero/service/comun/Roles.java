package edu.upb.semillero.service.comun;

/**
 * Los roles del sistema, unificados con el resto de los módulos: son los
 * mismos que devuelve el login común y los que viajan en el token.
 *
 * <ul>
 *   <li>{@code ADMINISTRADOR_GENERAL}: la tabla {@code administrador}.</li>
 *   <li>{@code JUGADOR}: la tabla {@code jugadores}.</li>
 *   <li>{@code USUARIO_CLUB}: la tabla {@code usuarios_club} (entrenador y
 *       demás personal de un club).</li>
 *   <li>{@code USUARIO_SISTEMA}: una cuenta operativa (organizador, mesa de
 *       control o consulta), marcada con una fila en {@code usuarios_roles}.</li>
 * </ul>
 *
 * <p>Torneos no inventa roles propios: reparte sus operaciones sobre estos
 * cuatro (HU 16). Un {@code USUARIO_SISTEMA} opera el torneo completo; el
 * {@code USUARIO_CLUB} solo los equipos de su club; el resto solo lee.
 */
public final class Roles {
    public static final String ADMINISTRADOR_GENERAL = "ADMINISTRADOR_GENERAL";
    public static final String JUGADOR = "JUGADOR";
    public static final String USUARIO_CLUB = "USUARIO_CLUB";
    public static final String USUARIO_SISTEMA = "USUARIO_SISTEMA";

    private Roles() {
    }
}

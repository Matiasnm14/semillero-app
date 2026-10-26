package edu.upb.semillero.service.comun;

import java.util.Set;

/**
 * Contrato del n&uacute;cleo com&uacute;n: usuarios, clubes y roles. Punto 11 del acuerdo
 * de la S1-04: la escribe torneos, la implementa ficha t&eacute;cnica, que ya hizo el
 * login.
 *
 * <p>Los c&oacute;digos de rol son ADMIN, ORGANIZADOR, ENTRENADOR, MESA_CONTROL y
 * CONSULTA. Por el punto 21 del acuerdo se derivan de lo que ya existe:
 * administrador de la tabla {@code administrador}, entrenador de
 * {@code usuario_trabaja.tipo_usuario_club = 'EN'}, y los otros tres de
 * {@code roles} m&aacute;s {@code usuarios_roles}, que todav&iacute;a no existen. Por eso
 * {@code tieneRol} tiene que consultar las tres fuentes.
 */
public interface ComunService {

    boolean usuarioExiste(Long usuarioId);

    boolean clubExiste(Long clubId);

    boolean tieneRol(Long usuarioId, String codigoRol);

    /** Para la restricci&oacute;n por propiedad: el entrenador solo maneja su club. */
    Set<Long> clubesDondeTrabaja(Long usuarioId);
}

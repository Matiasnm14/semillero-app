package edu.upb.semillero.service.seguridad;

import java.util.List;

/**
 * Lo que viaja en el token (tarea S2-01): el usuario, sus roles y, si trabaja
 * en un club, cuál. Sin datos personales más allá del nombre de cuenta.
 */
public record UsuarioAutenticado(Long usuarioId, String name, List<String> roles, Long clubId) {

    public boolean tieneRol(String codigo) {
        return roles.contains(codigo);
    }
}

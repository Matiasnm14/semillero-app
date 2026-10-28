package edu.upb.semillero.service.comun;

import edu.upb.semillero.repository.entity.Rol;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.UsuarioRol;
import edu.upb.semillero.repository.repos.RolRepository;
import edu.upb.semillero.repository.repos.UsuarioRepository;
import edu.upb.semillero.repository.repos.UsuarioRolRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 16 - Quién tiene qué rol (tarea S2-02). Lo maneja el administrador.
 *
 * <p>Solo toca {@code usuarios_roles}: los roles que salen de
 * {@code administrador} y de {@code usuario_trabaja} son de las otras tablas y
 * no se cambian desde acá (punto 21 del acuerdo de la S1-04).
 */
@Service
@Transactional(readOnly = true)
public class AsignacionRolesService {
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final UsuarioRolRepository usuariosRoles;
    private final ComunService comun;

    public AsignacionRolesService(UsuarioRepository usuarios, RolRepository roles,
            UsuarioRolRepository usuariosRoles, ComunService comun) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.usuariosRoles = usuariosRoles;
        this.comun = comun;
    }

    /** El catálogo de roles: los cinco de la HU 16. */
    public List<String> catalogo() {
        return roles.findAll().stream().map(Rol::getCodigo).sorted().toList();
    }

    /** Todos los roles del usuario, incluidos los que se derivan de otras tablas. */
    public List<String> rolesDe(Long usuarioId) {
        requireUsuario(usuarioId);
        return List.copyOf(comun.rolesDe(usuarioId));
    }

    /**
     * Reemplaza los roles asignados del usuario por los que vengan en la lista.
     * Mandar una lista vacía lo deja solo con lo que se derive de las otras
     * tablas, o en CONSULTA si no hay nada.
     */
    @Transactional
    public List<String> reemplazar(Long usuarioId, List<String> codigos) {
        Usuario usuario = requireUsuario(usuarioId);
        List<Rol> pedidos = codigos.stream().distinct()
                .map(codigo -> roles.findByCodigo(codigo)
                        .orElseThrow(() -> new NotFoundException("El rol " + codigo + " no existe")))
                .toList();
        usuariosRoles.deleteAll(usuariosRoles.findByUsuarioId(usuarioId));
        usuariosRoles.flush();
        for (Rol rol : pedidos) {
            UsuarioRol asignacion = new UsuarioRol();
            asignacion.setUsuario(usuario);
            asignacion.setRol(rol);
            asignacion.setFechaAlta(LocalDateTime.now());
            usuariosRoles.save(asignacion);
        }
        usuariosRoles.flush();
        return List.copyOf(comun.rolesDe(usuarioId));
    }

    private Usuario requireUsuario(Long usuarioId) {
        return usuarios.findById(usuarioId)
                .orElseThrow(() -> new NotFoundException(
                        "Usuario con id " + usuarioId + " no existe"));
    }
}

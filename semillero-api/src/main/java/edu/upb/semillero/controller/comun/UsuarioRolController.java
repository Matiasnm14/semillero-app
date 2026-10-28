package edu.upb.semillero.controller.comun;

import edu.upb.semillero.service.comun.AsignacionRolesService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU 16 - Roles de los usuarios (tarea S2-02). Es del núcleo común, no de
 * torneos, pero lo escribió torneos junto con las tablas de la migración V008,
 * porque sin esto los roles solo se podían cargar por SQL.
 *
 * <p>Solo el administrador puede asignar roles; la regla está en
 * {@code SeguridadConfiguration}.
 */
@RestController
public class UsuarioRolController {
    private final AsignacionRolesService service;

    public UsuarioRolController(AsignacionRolesService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/roles")
    public List<String> catalogo() {
        return service.catalogo();
    }

    @GetMapping("/api/v1/usuarios/{usuarioId}/roles")
    public List<String> rolesDe(@PathVariable @Positive Long usuarioId) {
        return service.rolesDe(usuarioId);
    }

    @PutMapping("/api/v1/usuarios/{usuarioId}/roles")
    public List<String> reemplazar(@PathVariable @Positive Long usuarioId,
            @RequestBody @NotNull List<String> codigos) {
        return service.reemplazar(usuarioId, codigos);
    }
}

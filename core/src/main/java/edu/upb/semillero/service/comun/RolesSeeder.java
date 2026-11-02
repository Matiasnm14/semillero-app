package edu.upb.semillero.service.comun;

import edu.upb.semillero.repository.entity.Rol;
import edu.upb.semillero.repository.repos.RolRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siembra los cinco roles de la HU 16. Idempotente: si el código ya existe no
 * hace nada, igual que {@code CriteriosDesempateSeeder}.
 *
 * <p>La migración V008 inserta lo mismo; esto deja la base consistente también
 * en las pruebas, donde Flyway está apagado.
 */
@Component
public class RolesSeeder implements ApplicationRunner {
    private static final Map<String, String> ROLES = new LinkedHashMap<>();

    static {
        ROLES.put(Roles.ADMIN, "Administrador");
        ROLES.put(Roles.ORGANIZADOR, "Organizador del torneo");
        ROLES.put(Roles.ENTRENADOR, "Entrenador");
        ROLES.put(Roles.MESA_CONTROL, "Mesa de control");
        ROLES.put(Roles.CONSULTA, "Consulta");
    }

    private final RolRepository repository;

    public RolesSeeder(RolRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ROLES.forEach((codigo, nombre) -> {
            if (repository.findByCodigo(codigo).isEmpty()) {
                Rol rol = new Rol();
                rol.setCodigo(codigo);
                rol.setNombre(nombre);
                repository.save(rol);
            }
        });
        repository.flush();
    }
}

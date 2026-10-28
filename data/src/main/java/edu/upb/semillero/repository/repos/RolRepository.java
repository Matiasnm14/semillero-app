package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Rol;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolRepository extends JpaRepository<Rol, Long> {
    Optional<Rol> findByCodigo(String codigo);
}

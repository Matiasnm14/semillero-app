package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Administrador;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdministradorRepository extends JpaRepository<Administrador, Long> {
    Optional<Administrador> findFirstByUsuarioId(Long usuarioId);
}

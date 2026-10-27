package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.UsuarioTrabaja;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioTrabajaRepository extends JpaRepository<UsuarioTrabaja, Long> {
    Optional<UsuarioTrabaja> findFirstByUsuarioClubId(Long usuarioClubId);
}

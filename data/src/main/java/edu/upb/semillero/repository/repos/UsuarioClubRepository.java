package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.UsuarioClub;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioClubRepository extends JpaRepository<UsuarioClub, Long> {
    Optional<UsuarioClub> findFirstByUsuarioId(Long usuarioId);
}

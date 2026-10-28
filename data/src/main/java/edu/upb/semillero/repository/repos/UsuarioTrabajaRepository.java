package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.UsuarioTrabaja;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UsuarioTrabajaRepository extends JpaRepository<UsuarioTrabaja, Long> {
    Optional<UsuarioTrabaja> findFirstByUsuarioClubId(Long usuarioClubId);

    /** Los clubes donde trabaja un usuario; la HU 16 lo usa para los permisos. */
    List<UsuarioTrabaja> findByUsuarioClubUsuarioId(Long usuarioId);
}

package edu.upb.semillero.repository.repos.scouting;

import edu.upb.semillero.repository.entity.scouting.PreferenciaContacto;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface PreferenciaContactoRepository extends JpaRepository<PreferenciaContacto, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PreferenciaContacto p where p.jugadorId = :id")
    Optional<PreferenciaContacto> bloquear(Long id);
}

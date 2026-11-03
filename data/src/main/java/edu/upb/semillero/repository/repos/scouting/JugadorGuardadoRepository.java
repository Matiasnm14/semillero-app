package edu.upb.semillero.repository.repos.scouting;

import edu.upb.semillero.repository.entity.scouting.JugadorGuardado;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface JugadorGuardadoRepository extends JpaRepository<JugadorGuardado, UUID> {
    Optional<JugadorGuardado> findByClubIdAndJugadorId(Long clubId, Long jugadorId);
    Page<JugadorGuardado> findByClubId(Long clubId, Pageable pageable);
}

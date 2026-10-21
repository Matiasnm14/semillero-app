package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.Planilla;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanillaRepository extends JpaRepository<Planilla, Long> {
    Optional<Planilla> findByPartidoId(Long partidoId);
}

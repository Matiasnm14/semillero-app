package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.Reglamento;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReglamentoRepository extends JpaRepository<Reglamento, Long> {
    Optional<Reglamento> findByTorneoId(Long torneoId);
}

package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.Reprogramacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReprogramacionRepository extends JpaRepository<Reprogramacion, Long> {
    List<Reprogramacion> findByPartidoIdOrderByFechaAsc(Long partidoId);
}

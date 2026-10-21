package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.ReglamentoDesempate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReglamentoDesempateRepository extends JpaRepository<ReglamentoDesempate, Long> {
    List<ReglamentoDesempate> findByReglamentoIdOrderByOrdenAsc(Long reglamentoId);
}

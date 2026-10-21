package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.PlanillaCuerpoTecnico;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanillaCuerpoTecnicoRepository extends JpaRepository<PlanillaCuerpoTecnico, Long> {
    List<PlanillaCuerpoTecnico> findByPlanillaId(Long planillaId);
}

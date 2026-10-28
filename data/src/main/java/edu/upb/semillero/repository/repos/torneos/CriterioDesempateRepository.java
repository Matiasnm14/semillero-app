package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.CriterioDesempate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CriterioDesempateRepository extends JpaRepository<CriterioDesempate, Long> {
    Optional<CriterioDesempate> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    Page<CriterioDesempate> findAllByOrderByCodigoAsc(Pageable pageable);
}

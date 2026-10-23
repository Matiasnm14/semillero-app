package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.Cancha;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CanchaRepository extends JpaRepository<Cancha, Long> {
    Page<Cancha> findBySedeId(Long sedeId, Pageable pageable);

    Optional<Cancha> findBySedeIdAndNombre(Long sedeId, String nombre);
}

package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TorneoCategoriaRepository extends JpaRepository<TorneoCategoria, Long> {
    Page<TorneoCategoria> findByTorneoId(Long torneoId, Pageable pageable);

    Optional<TorneoCategoria> findByIdAndTorneoId(Long id, Long torneoId);

    Optional<TorneoCategoria> findByTorneoIdAndNombre(Long torneoId, String nombre);

    boolean existsByTorneoId(Long torneoId);
}

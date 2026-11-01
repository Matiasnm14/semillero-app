package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Fichado;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FichadoRepository extends JpaRepository<Fichado, Long> {
    boolean existsByJugadorIdAndClubIdAndFechaFinalFichajeIsNull(Long jugadorId, Long clubId);

    Optional<Fichado> findFirstByJugadorIdAndFechaFinalFichajeIsNullOrderByFechaFichajeDesc(Long jugadorId);
}

package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Fichado;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FichadoRepository extends JpaRepository<Fichado, Long> {
    boolean existsByJugadorIdAndClubIdAndFechaFinalFichajeIsNull(Long jugadorId, Long clubId);

    List<Fichado> findByClubIdInAndFechaFinalFichajeIsNull(Collection<Long> clubIds);

    Optional<Fichado> findFirstByJugadorIdAndFechaFinalFichajeIsNullOrderByFechaFichajeDesc(Long jugadorId);

    List<Fichado> findByJugadorId(Long jugadorId);

    List<Fichado> findByClubId(Long clubId);
}

package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.Torneo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TorneoRepository extends JpaRepository<Torneo, Long> {

    /** HU 15: los torneos de una temporada, para {@code TorneosConsultaService}. */
    List<Torneo> findByTemporadaOrderByIdAsc(Short temporada);
}

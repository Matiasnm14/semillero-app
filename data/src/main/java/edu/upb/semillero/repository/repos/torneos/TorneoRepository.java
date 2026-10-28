package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.Torneo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TorneoRepository extends JpaRepository<Torneo, Long> {
}

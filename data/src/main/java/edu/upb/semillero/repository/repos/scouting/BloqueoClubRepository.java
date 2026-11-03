package edu.upb.semillero.repository.repos.scouting;

import edu.upb.semillero.repository.entity.scouting.BloqueoClub;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BloqueoClubRepository extends JpaRepository<BloqueoClub, String> {
    Page<BloqueoClub> findByJugadorIdAndActivoTrue(Long jugadorId, Pageable pageable);
}

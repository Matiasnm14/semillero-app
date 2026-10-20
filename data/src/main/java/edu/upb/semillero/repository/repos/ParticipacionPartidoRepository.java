package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.ParticipacionPartido;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.Optional;

public interface ParticipacionPartidoRepository extends JpaRepository<ParticipacionPartido, Long> {
    Optional<ParticipacionPartido> findByPartidoIdAndJugadorId(Long partidoId, Long jugadorId);

    boolean existsByPartidoIdAndClubIdNotIn(Long partidoId, Collection<Long> clubesId);

}

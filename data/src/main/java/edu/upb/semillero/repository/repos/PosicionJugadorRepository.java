package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.PosicionJugador;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Collection;

public interface PosicionJugadorRepository extends JpaRepository<PosicionJugador, Long> {
    List<PosicionJugador> findByJugadorId(Long jugadorId);
    List<PosicionJugador> findByJugadorIdIn(Collection<Long> jugadorIds);
}

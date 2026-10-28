package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.Sancion;
import edu.upb.semillero.repository.enums.torneos.EstadoSancion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SancionRepository extends JpaRepository<Sancion, Long> {
    List<Sancion> findByListaBuenaFeIdAndEstado(Long listaBuenaFeId, EstadoSancion estado);

    /** Sanciones del jugador en cualquier torneo: la lista de buena fe las mira al darlo de alta. */
    List<Sancion> findByListaBuenaFeJugadorIdAndEstado(Long jugadorId, EstadoSancion estado);

    List<Sancion> findByEstado(EstadoSancion estado);
}

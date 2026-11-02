package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.Sancion;
import edu.upb.semillero.repository.enums.torneos.EstadoSancion;
import edu.upb.semillero.repository.enums.torneos.OrigenSancion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SancionRepository extends JpaRepository<Sancion, Long> {
    List<Sancion> findByListaBuenaFeIdAndEstado(Long listaBuenaFeId, EstadoSancion estado);

    /** Sanciones del jugador en cualquier torneo: la lista de buena fe las mira al darlo de alta. */
    List<Sancion> findByListaBuenaFeJugadorIdAndEstado(Long jugadorId, EstadoSancion estado);

    List<Sancion> findByEstado(EstadoSancion estado);

    /** Tarea S4-04: las sanciones del torneo, de la más reciente a la más antigua. */
    List<Sancion> findByListaBuenaFeTorneoIdOrderByFechaRegistroDescIdDesc(Long torneoId);

    /** HU 14 y 15: el historial disciplinario de un jugador, en todos los torneos. */
    List<Sancion> findByListaBuenaFeJugadorIdOrderByFechaRegistroDescIdDesc(Long jugadorId);

    /** Tarea S4-03: las sanciones de los jugadores de un equipo, para descontar fechas. */
    List<Sancion> findByListaBuenaFeInscripcionEquipoIdAndEstadoIn(Long inscripcionEquipoId,
            List<EstadoSancion> estados);

    /** Tareas S4-01 y S4-02: las sanciones que generó un partido. */
    List<Sancion> findByPartidoIdOrderByIdAsc(Long partidoId);

    /** Tarea S4-01: las suspensiones por acumulación que ya tiene el jugador en el torneo. */
    List<Sancion> findByListaBuenaFeIdAndOrigenOrderByIdAsc(Long listaBuenaFeId,
            OrigenSancion origen);
}

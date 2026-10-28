package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.EventoPartido;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoPartidoRepository extends JpaRepository<EventoPartido, Long> {
    List<EventoPartido> findByPartidoIdOrderByMinutoAsc(Long partidoId);

    /** Los eventos en el orden en que pasaron; dos del mismo minuto, en el orden en que se cargaron. */
    List<EventoPartido> findByPartidoIdOrderByMinutoAscIdAsc(Long partidoId);

    /** Fair play de la tabla (tarea S3-13): las tarjetas de todos los partidos cerrados. */
    List<EventoPartido> findByPartidoIdIn(Collection<Long> partidoIds);

    /** Un jugador con eventos no se puede sacar de la planilla (tarea S3-02). */
    boolean existsByParticipacionIdOrParticipacionRelacionadaId(Long participacionId,
            Long participacionRelacionadaId);
}

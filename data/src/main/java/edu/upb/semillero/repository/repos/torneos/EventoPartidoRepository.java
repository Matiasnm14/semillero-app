package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.EventoPartido;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.torneos.TipoEventoPartido;
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

    /** HU 12: los eventos de los partidos cerrados de una categoría (tarea S4-11). */
    List<EventoPartido> findByPartidoTorneoCategoriaIdAndPartidoEstado(Long torneoCategoriaId,
            EstadoPartido estado);

    /** HU 12: los eventos de un jugador en los partidos cerrados de un torneo (tarea S4-12). */
    List<EventoPartido> findByParticipacionJugadorIdAndPartidoTorneoCategoriaTorneoIdAndPartidoEstado(
            Long jugadorId, Long torneoId, EstadoPartido estado);

    /** HU 12: las asistencias y entradas de un jugador en un torneo (tarea S4-12). */
    List<EventoPartido> findByParticipacionRelacionadaJugadorIdAndPartidoTorneoCategoriaTorneoIdAndPartidoEstado(
            Long jugadorId, Long torneoId, EstadoPartido estado);

    /** Tarea S4-01: las amarillas que acumula un jugador en los partidos cerrados del torneo. */
    long countByTipoAndParticipacionJugadorIdAndPartidoTorneoCategoriaTorneoIdAndPartidoEstado(
            TipoEventoPartido tipo, Long jugadorId, Long torneoId, EstadoPartido estado);
}

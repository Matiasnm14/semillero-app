package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.ParticipacionPartido;
import edu.upb.semillero.repository.enums.EstadoPartido;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * La planilla de jugadores del partido, que es {@code participaciones_partido}
 * (decisión 3.6 del contexto). Torneos escribe {@code es_titular},
 * {@code dorsal}, {@code capitan}, los minutos de entrada y salida y
 * {@code minutos_jugados} (tareas S3-02 y S3-08), por sus propios endpoints
 * según el punto 5 del acuerdo de la S1-04. Nunca toca {@code velocidad_prom} ni
 * {@code distancia_recorrida}, que son del microservicio de video.
 *
 * <p>Extiende {@code Repository} para exponer solo lo que la planilla necesita;
 * el CRUD compartido sigue siendo de {@code ParticipacionPartidoRepository}.
 */
public interface ParticipacionPlanillaRepository extends Repository<ParticipacionPartido, Long> {

    ParticipacionPartido save(ParticipacionPartido participacion);

    void delete(ParticipacionPartido participacion);

    void flush();

    List<ParticipacionPartido> findByPartidoIdOrderByIdAsc(Long partidoId);

    Optional<ParticipacionPartido> findByPartidoIdAndJugadorId(Long partidoId, Long jugadorId);

    /** Tarea S4-12: los partidos cerrados de un jugador en un torneo, con sus minutos. */
    List<ParticipacionPartido> findByJugadorIdAndPartidoTorneoCategoriaTorneoIdAndPartidoEstado(
            Long jugadorId, Long torneoId, EstadoPartido estado);

    /** Tarea S4-11: quién jugó los partidos cerrados de una categoría. */
    List<ParticipacionPartido> findByPartidoTorneoCategoriaIdAndPartidoEstado(Long torneoCategoriaId,
            EstadoPartido estado);
}

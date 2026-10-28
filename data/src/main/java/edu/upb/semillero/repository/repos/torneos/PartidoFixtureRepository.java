package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.FaseTorneo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Los partidos que escribe el fixture de torneos (tareas S2-14 a S2-17).
 *
 * <p>{@code partidos} es una tabla de la BD acordada, pero el punto 5 del
 * acuerdo de la S1-04 dice que las columnas que agregó torneos las escribe
 * torneos por sus propios endpoints, y el fixture es justamente eso. El CRUD
 * compartido sigue siendo de {@code PartidoRepository}, que no se toca.
 *
 * <p>Extiende {@code Repository} para exponer solo lo que el fixture necesita.
 */
public interface PartidoFixtureRepository extends Repository<Partido, Long> {

    Partido save(Partido partido);

    Partido saveAndFlush(Partido partido);

    void delete(Partido partido);

    void flush();

    Optional<Partido> findById(Long id);

    /** El fixture de una categoría, en el orden en que se juega. */
    List<Partido> findByTorneoCategoriaIdOrderByJornadaAscFechaInicioAsc(Long torneoCategoriaId);

    List<Partido> findByTorneoCategoriaIdAndJornada(Long torneoCategoriaId, Short jornada);

    List<Partido> findByTorneoCategoriaIdAndFase(Long torneoCategoriaId, FaseTorneo fase);

    /**
     * Todo lo que ya está agendado en una ventana de tiempo, de cualquier torneo:
     * con esto se detectan los choques de cancha y de equipo (tarea S2-16).
     */
    List<Partido> findByFechaInicioBetween(LocalDateTime desde, LocalDateTime hasta);

    long countByTorneoCategoriaId(Long torneoCategoriaId);

    /**
     * Punto 13 del acuerdo y tarea S4-09: los partidos cerrados desde una fecha,
     * para que el microservicio de video los lea por {@code fecha_cierre} si no
     * le llegó el aviso.
     */
    List<Partido> findByEstadoAndFechaCierreAfterOrderByFechaCierreAsc(EstadoPartido estado,
            LocalDateTime desde);
}

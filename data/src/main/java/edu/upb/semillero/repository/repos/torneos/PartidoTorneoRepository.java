package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.enums.EstadoPartido;
import java.util.Collection;
import org.springframework.data.repository.Repository;

/**
 * Vista de solo lectura de {@code partidos} para torneos, sobre las columnas
 * que agrega la tarea S1-06.
 *
 * <p>Extiende {@code Repository} y no {@code JpaRepository} a propósito: torneos
 * no escribe en tablas de otro módulo, solo consulta. El CRUD de partidos sigue
 * siendo de {@code PartidoRepository}, que no se toca.
 */
public interface PartidoTorneoRepository extends Repository<Partido, Long> {

    /** Tarea S1-18: si hay partidos en estos estados, la cancha no se borra. */
    boolean existsByCanchaIdAndEstadoIn(Long canchaId, Collection<EstadoPartido> estados);

    long countByCanchaId(Long canchaId);

    long countByTorneoCategoriaId(Long torneoCategoriaId);
}

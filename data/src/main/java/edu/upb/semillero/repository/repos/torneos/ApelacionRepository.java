package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.Apelacion;
import edu.upb.semillero.repository.enums.torneos.EstadoApelacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApelacionRepository extends JpaRepository<Apelacion, Long> {
    List<Apelacion> findBySancionId(Long sancionId);

    List<Apelacion> findBySancionIdOrderByIdAsc(Long sancionId);

    /** Tarea S4-05: una sola apelación pendiente por sanción. */
    boolean existsBySancionIdAndEstado(Long sancionId, EstadoApelacion estado);
}

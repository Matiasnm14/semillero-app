package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.UsuarioTrabaja;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Lectura de {@code usuario_trabaja}, que es de la BD acordada entre grupos:
 * quiénes trabajan en un club. Torneos la usa para avisar a los equipos cuando
 * se reprograma un partido (tarea S3-19).
 *
 * <p>Extiende {@code Repository} y no {@code JpaRepository} a propósito, igual
 * que {@code FichajeConsultaRepository}: torneos solo lee esta tabla.
 */
public interface VinculoClubConsultaRepository extends Repository<UsuarioTrabaja, Long> {

    List<UsuarioTrabaja> findByClubId(Long clubId);
}

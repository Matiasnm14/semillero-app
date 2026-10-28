package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.Fichado;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Lectura de la tabla {@code fichado}, que es de la BD acordada entre grupos.
 * Torneos la necesita para la validación "el jugador está fichado en el club"
 * de la lista de buena fe (HU 5), mientras ficha técnica no implemente
 * {@code FichaTecnicaService} (punto 10 del acuerdo de la S1-04).
 *
 * <p>Extiende {@code Repository} y no {@code JpaRepository} a propósito: así
 * solo expone las dos consultas que torneos usa y no hay forma de escribir en
 * una tabla de otro módulo desde acá.
 */
public interface FichajeConsultaRepository extends Repository<Fichado, Long> {

    List<Fichado> findByJugadorId(Long jugadorId);

    List<Fichado> findByClubId(Long clubId);
}

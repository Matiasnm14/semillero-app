package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.Jugador;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Lectura de {@code jugadores}, que es de la BD acordada y la mantiene ficha
 * técnica: qué jugadores tiene una cuenta. La app (HU 14) entra con la cuenta
 * del jugador y necesita saber cuál es para mostrarle sus partidos y su
 * suspensión.
 *
 * <p>Extiende {@code Repository} y no {@code JpaRepository} a propósito, igual
 * que {@code FichajeConsultaRepository}: torneos solo lee esta tabla.
 */
public interface JugadorConsultaRepository extends Repository<Jugador, Long> {

    List<Jugador> findByUsuarioIdOrderByIdAsc(Long usuarioId);
}

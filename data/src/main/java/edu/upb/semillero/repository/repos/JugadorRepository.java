package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Jugador;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface JugadorRepository extends JpaRepository<Jugador, Long> {
    Optional<Jugador> findFirstByUsuarioId(Long usuarioId);
}

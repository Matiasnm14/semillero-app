package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.DocumentoJugador;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoJugadorRepository extends JpaRepository<DocumentoJugador, Long> {
    List<DocumentoJugador> findByJugadorId(Long jugadorId);

    Page<DocumentoJugador> findByJugadorIdIn(Collection<Long> jugadorIds, Pageable pageable);
}

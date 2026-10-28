package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.EventoPartido;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoPartidoRepository extends JpaRepository<EventoPartido, Long> {
    List<EventoPartido> findByPartidoIdOrderByMinutoAsc(Long partidoId);
}

package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Partido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartidoRepository extends JpaRepository<Partido, Long> {
}

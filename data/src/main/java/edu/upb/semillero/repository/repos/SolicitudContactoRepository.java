package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.SolicitudContacto;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudContactoRepository extends JpaRepository<SolicitudContacto, Long> {
    Page<SolicitudContacto> findByClubIdOrderByFechaEnvioDesc(Long clubId, Pageable pageable);
    Page<SolicitudContacto> findByJugadorIdOrderByFechaEnvioDesc(Long jugadorId, Pageable pageable);
    Optional<SolicitudContacto> findByIdAndJugadorId(Long id, Long jugadorId);
}

package edu.upb.semillero.repository.repos.scouting;

import edu.upb.semillero.repository.entity.scouting.EventoContacto;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EventoContactoRepository extends JpaRepository<EventoContacto, UUID> {
    Page<EventoContacto> findBySolicitudId(UUID solicitudId, Pageable pageable);
}

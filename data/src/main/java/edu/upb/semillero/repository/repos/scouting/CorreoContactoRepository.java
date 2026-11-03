package edu.upb.semillero.repository.repos.scouting;

import edu.upb.semillero.repository.entity.scouting.CorreoContacto;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CorreoContactoRepository extends JpaRepository<CorreoContacto, UUID> {
    java.util.List<CorreoContacto> findByEventoSolicitudId(UUID solicitudId);
}

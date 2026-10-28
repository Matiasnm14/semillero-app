package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Notificacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByUsuarioIdOrderByFechaDesc(Long usuarioId);

    long countByUsuarioIdAndLeidaFalse(Long usuarioId);
}

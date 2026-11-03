package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Notificacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByUsuarioIdOrderByFechaDesc(Long usuarioId);

    long countByUsuarioIdAndLeidaFalse(Long usuarioId);

    Page<Notificacion> findByUsuarioIdAndLeidaOrderByFechaDesc(Long usuarioId, Boolean leida, Pageable pageable);

    Page<Notificacion> findByUsuarioIdOrderByFechaDesc(Long usuarioId, Pageable pageable);

    /**
     * Las de un usuario, de la más nueva a la más vieja. Desempata por id porque
     * los avisos de un mismo cierre salen en el mismo instante.
     */
    List<Notificacion> findByUsuarioIdOrderByFechaDescIdDesc(Long usuarioId);

    List<Notificacion> findByUsuarioIdAndLeidaFalseOrderByFechaDescIdDesc(Long usuarioId);
}

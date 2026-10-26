package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Auditoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {
    List<Auditoria> findByTablaAndRegistroIdOrderByFechaDesc(String tabla, Long registroId);

    List<Auditoria> findByModuloOrderByFechaDesc(String modulo);
}

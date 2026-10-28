package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Auditoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {
    /**
     * El historial de un registro, del más reciente al más antiguo. Desempata
     * por id porque dos filas seguidas pueden caer en el mismo instante y el
     * orden quedaría indefinido.
     */
    List<Auditoria> findByTablaAndRegistroIdOrderByFechaDescIdDesc(String tabla, Long registroId);

    List<Auditoria> findByModuloOrderByFechaDesc(String modulo);
}

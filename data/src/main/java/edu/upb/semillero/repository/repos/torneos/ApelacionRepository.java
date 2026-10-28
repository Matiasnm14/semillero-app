package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.Apelacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApelacionRepository extends JpaRepository<Apelacion, Long> {
    List<Apelacion> findBySancionId(Long sancionId);
}

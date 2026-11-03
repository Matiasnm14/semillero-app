package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.InscripcionPrueba;
import edu.upb.semillero.repository.enums.EstadoInscripcionPrueba;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InscripcionPruebaRepository extends JpaRepository<InscripcionPrueba, Long> {
    List<InscripcionPrueba> findByPruebaIdOrderByFechaPostulacionDesc(Long pruebaId);
    long countByPruebaIdAndEstadoNot(Long pruebaId, EstadoInscripcionPrueba estado);
    long countByPruebaId(Long pruebaId);
    boolean existsByPruebaIdAndJugadorId(Long pruebaId, Long jugadorId);
    Optional<InscripcionPrueba> findByIdAndJugadorId(Long id, Long jugadorId);
}

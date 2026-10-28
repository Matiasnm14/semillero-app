package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import edu.upb.semillero.repository.enums.torneos.EstadoInscripcion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InscripcionEquipoRepository extends JpaRepository<InscripcionEquipo, Long> {
    Page<InscripcionEquipo> findByTorneoCategoriaId(Long torneoCategoriaId, Pageable pageable);

    Page<InscripcionEquipo> findByTorneoCategoriaIdAndEstado(Long torneoCategoriaId,
            EstadoInscripcion estado, Pageable pageable);

    Page<InscripcionEquipo> findByTorneoCategoriaTorneoId(Long torneoId, Pageable pageable);

    Page<InscripcionEquipo> findByTorneoCategoriaTorneoIdAndEstado(Long torneoId,
            EstadoInscripcion estado, Pageable pageable);

    Page<InscripcionEquipo> findByEstado(EstadoInscripcion estado, Pageable pageable);

    Optional<InscripcionEquipo> findByTorneoCategoriaIdAndClubId(Long torneoCategoriaId, Long clubId);

    /** Los equipos que entran al fixture: solo los aprobados (HU 6). */
    List<InscripcionEquipo> findByTorneoCategoriaIdAndEstadoOrderByIdAsc(Long torneoCategoriaId,
            EstadoInscripcion estado);

    long countByTorneoCategoriaId(Long torneoCategoriaId);
}

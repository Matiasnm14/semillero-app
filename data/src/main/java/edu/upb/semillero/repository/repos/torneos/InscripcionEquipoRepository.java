package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InscripcionEquipoRepository extends JpaRepository<InscripcionEquipo, Long> {
    Page<InscripcionEquipo> findByTorneoCategoriaId(Long torneoCategoriaId, Pageable pageable);

    Optional<InscripcionEquipo> findByTorneoCategoriaIdAndClubId(Long torneoCategoriaId, Long clubId);

    long countByTorneoCategoriaId(Long torneoCategoriaId);
}

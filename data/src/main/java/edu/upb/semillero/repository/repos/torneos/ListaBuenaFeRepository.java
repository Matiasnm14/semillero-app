package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.ListaBuenaFe;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListaBuenaFeRepository extends JpaRepository<ListaBuenaFe, Long> {
    Page<ListaBuenaFe> findByInscripcionEquipoId(Long inscripcionEquipoId, Pageable pageable);

    List<ListaBuenaFe> findByInscripcionEquipoIdOrderByDorsalAsc(Long inscripcionEquipoId);

    Optional<ListaBuenaFe> findByTorneoIdAndJugadorId(Long torneoId, Long jugadorId);

    Optional<ListaBuenaFe> findByInscripcionEquipoIdAndDorsal(Long inscripcionEquipoId, Short dorsal);

    long countByInscripcionEquipoId(Long inscripcionEquipoId);
}
